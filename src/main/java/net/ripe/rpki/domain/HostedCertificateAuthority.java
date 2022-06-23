package net.ripe.rpki.domain;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.CertificateAuthorityEvent;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateProvisioningMessage;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.ripencc.support.event.DefaultEventDelegate;
import net.ripe.rpki.ripencc.support.event.EventDelegate;
import net.ripe.rpki.ripencc.support.event.EventSubscription;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.util.DBComponent;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


/**
 * Hosted Certificate Authorities
 */
@Entity
@Slf4j
public abstract class HostedCertificateAuthority extends CertificateAuthority implements ParentCertificateAuthority {

    public static final EventDelegate<CertificateAuthorityEvent> EVENTS = new DefaultEventDelegate<>();

    public static EventSubscription subscribe(final CertificateAuthorityEventVisitor listener) {
        return EVENTS.subscribe(event -> event.accept(listener));
    }

    @NotNull
    @Column(name = "last_issued_serial")
    private BigInteger lastIssuedSerial = BigInteger.ZERO;

    @OneToOne(mappedBy = "certificateAuthority", cascade = {CascadeType.ALL})
    private UpStreamCARequestEntity upStreamCARequestEntity;

    @NotNull
    @Column(nullable = false, name = "random_serial_increment")
    private int randomSerialIncrement;

    @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL )
    @JoinColumn(name = "ca_id", nullable = false)
    private Set<KeyPairEntity> keyPairs = new HashSet<>();

    /**
     * Flag to indicate that the incoming certificate was updated and the manifest and CRL may need to be re-issued.
     *
     * Set whenever a new incoming resource certificate is received ({@link #processCertificateIssuanceRequest}),
     * cleared once the manifest and CRL have been checked (and re-issued if needed), see
     * {@link net.ripe.rpki.services.impl.handlers.IssueUpdatedManifestAndCrlCommandHandler IssueUpdatedManifestAndCrlCommandHandler} and
     * {@link net.ripe.rpki.services.impl.background.PublicRepositoryPublicationServiceBean PublicRepositoryPublicationServiceBean}.
     */
    @Getter
    @Column(name = "manifest_and_crl_check_needed")
    private boolean manifestAndCrlCheckNeeded;

    protected HostedCertificateAuthority() {
    }

    protected HostedCertificateAuthority(long id, X500Principal name, ParentCertificateAuthority parent, int randomSerialIncrement) {
        super(id, parent, name);
        Validate.notNull(name, "name is required");
        Validate.isTrue(randomSerialIncrement > 0, "randomSerialIncrement must be positive");
        this.randomSerialIncrement = randomSerialIncrement;
        this.manifestAndCrlCheckNeeded = true;
    }

    @Override
    public HostedCertificateAuthorityData toData() {
        TrustAnchorRequest upStreamCARequest = upStreamCARequestEntity != null ? upStreamCARequestEntity.getUpStreamCARequest() : null;

        final List<KeyPairData> keys = getKeyPairs().stream()
            .map(KeyPairEntity::toData)
            .collect(Collectors.toList());

        return new HostedCertificateAuthorityData(
            getVersionedId(), getName(), getUuid(),
            getParent() == null ? null : getParent().getId(),
            getType(),
            getCertifiedResources(), upStreamCARequest, keys);
    }

    public BigInteger getLastIssuedSerial() {
        return lastIssuedSerial;
    }


    public void removeKeyPair(final String name) {
        Optional<KeyPairEntity> keyPair = findKeyPairByName(name);
        Validate.isTrue(keyPair.isPresent(), "Key pair is not present '" + name + "'");

        keyPair.ifPresent(kp -> {
            Validate.isTrue(kp.isRemovable(), "Key pair is in use '" + name + "'");
            keyPairs.remove(kp);
        });
    }

    public Collection<KeyPairEntity> getKeyPairs() {
        return keyPairs;
    }

    public Optional<KeyPairEntity> findKeyPairByName(String keyPairName) {
        return keyPairs.stream().filter(keyPair -> keyPair.hasName(keyPairName)).findFirst();
    }

    public Optional<KeyPairEntity> findKeyPairByEncodedPublicKey(String encodedPublicKey) {
        return keyPairs.stream().filter(keyPair -> keyPair.getEncodedKeyIdentifier().equals(encodedPublicKey)).findFirst();
    }

    public Optional<KeyPairEntity> findKeyPairByPublicKey(PublicKey publicKey) {
        return findKeyPairByEncodedPublicKey(KeyPairUtil.getEncodedKeyIdentifier(publicKey));
    }

    public Optional<KeyPairEntity> findCurrentKeyPair() {
        return findFirstKeyPairWithStatus(KeyPairStatus.CURRENT);
    }

    private Optional<KeyPairEntity> findFirstKeyPairWithStatus(KeyPairStatus status) {
        return getKeyPairs().stream()
            .filter(keyPair -> keyPair.getStatus().equals(status))
            .findFirst();
    }

    public IpResourceSet getCertifiedResources() {
        return findCurrentKeyPair()
            .flatMap(KeyPairEntity::findCurrentIncomingCertificate)
            .map(ResourceCertificate::getResources)
            .orElse(new IpResourceSet());
    }

    public KeyPairEntity getCurrentKeyPair() {
        return findCurrentKeyPair().orElseThrow(() -> new CertificateAuthorityException("No active key pair available to sign requested certificate"));
    }

    public void validateChildResourceSet(IpResourceSet childResources) {
        IpResourceSet parentResources = getCertifiedResources();
        if (!parentResources.contains(childResources)) {
            IpResourceSet bad = new IpResourceSet(childResources);
            bad.removeAll(parentResources);
            throw new CertificateAuthorityException("child resources '" + bad + "' are not contained in parent resources");
        }
    }


    public UpStreamCARequestEntity getUpStreamCARequestEntity() {
        return upStreamCARequestEntity;
    }

    public void setUpStreamCARequestEntity(UpStreamCARequestEntity upStreamCARequestEntity) {
        this.upStreamCARequestEntity = upStreamCARequestEntity;
    }

    public void setLastIssuedSerial(BigInteger lastIssuedSerial) {
        this.lastIssuedSerial = lastIssuedSerial;
    }

    public void roaConfigurationUpdated() {
        this.manifestAndCrlCheckNeeded = true;
    }

    public void manifestAndCrlCheckCompleted() {
        this.manifestAndCrlCheckNeeded = false;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("id", getId())
            .append("type", getType())
            .append("name", getName())
            .toString();
    }

    /**
     * This removed the revoked key, and ALL of its products.
     * Idea is to refactor domain so that we do not need repositories anymore,
     * in which case we could just have cascading delete.
     */
    public Optional<KeyPairData> deleteRevokedKey(String encodedSKI,
                                                  CrlEntityRepository crlEntityRepository,
                                                  ManifestEntityRepository manifestEntityRepository,
                                                  RoaEntityRepository roaRepository,
                                                  ResourceCertificateRepository resourceCertificateRepository,
                                                  PublishedObjectRepository publishedObjectRepository) {
        final Optional<KeyPairEntity> keyPair = findKeyPairByEncodedPublicKey(encodedSKI);
        keyPair.ifPresent(kp -> {
            Validate.isTrue(kp.isRevoked(), "Can only archive revoked key");

            publishedObjectRepository.withdrawAllForDeletedKeyPair(kp);

            crlEntityRepository.deleteByKeyPair(kp);
            manifestEntityRepository.deleteByKeyPairEntity(kp);
            roaRepository.deleteByCertificateSigningKeyPair(kp);
            resourceCertificateRepository.deleteOutgoingCertificatesForRevokedKeyPair(kp);

            kp.deleteIncomingResourceCertificate();

            keyPairs.remove(kp);
        });
        return keyPair.map(KeyPairEntity::toData);
    }

    @Override
    public boolean isCertificateIssuanceNeeded(CertificateIssuanceRequest request, ValidityPeriod requestedValidityPeriod, ResourceCertificateRepository resourceCertificateRepository) {
        KeyPairEntity currentKeyPair = getCurrentKeyPair();
        OutgoingResourceCertificate latestOutgoingCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(request.getSubjectPublicKey(), currentKeyPair);
        if (latestOutgoingCertificate == null) {
            log.info("No current certificate for resource class " + DEFAULT_RESOURCE_CLASS + " and current key pair, requesting new certificate");
            return true;
        }

        return resourcesChanged(request, latestOutgoingCertificate)
            || subjectInformationAccessChanged(request, latestOutgoingCertificate)
            || signingCertificateLocationChanged(currentKeyPair, latestOutgoingCertificate)
            || newValidityTimeApplies(requestedValidityPeriod, latestOutgoingCertificate);
    }

    @Override
    public boolean isCertificateRevocationNeeded(CertificateRevocationRequest request, ResourceCertificateRepository resourceCertificateRepository) {
        return !resourceCertificateRepository.findCurrentCertificatesBySubjectPublicKey(request.getSubjectPublicKey()).isEmpty();
    }

    private boolean resourcesChanged(CertificateIssuanceRequest request, OutgoingResourceCertificate latestOutgoingCertificate) {
        if (Objects.equals(request.getResources(), latestOutgoingCertificate.getResources())) {
            return false;
        }
        log.info(
            "Current certificate for resource class {}, has different resources. Was: {}, will request: {}",
            DEFAULT_RESOURCE_CLASS, latestOutgoingCertificate.getResources(), request.getResources()
        );
        return true;
    }

    private boolean subjectInformationAccessChanged(CertificateIssuanceRequest request, OutgoingResourceCertificate currentCertificate) {
        // Sort by key since order across different keys does not matter. If the same key appears multiple times the order does matter,
        // but since the sorting is stable this will be detected.
        List<X509CertificateInformationAccessDescriptor> a = Arrays.stream(request.getSubjectInformationAccess()).sorted(Comparator.comparing(x -> x.getMethod().getId())).collect(Collectors.toList());
        List<X509CertificateInformationAccessDescriptor> b = Arrays.stream(currentCertificate.getSia()).sorted(Comparator.comparing(x -> x.getMethod().getId())).collect(Collectors.toList());
        if (Objects.equals(a, b)) {
            return false;
        }
        log.info("Certificate subject access information has changed, certificate needs to be re-issued");
        return true;
    }

    private boolean signingCertificateLocationChanged(KeyPairEntity signingKeyPair, OutgoingResourceCertificate currentCertificate) {
        URI signingCertificateUri = signingKeyPair.findCurrentIncomingCertificate().map(IncomingResourceCertificate::getPublicationUri).orElse(URI.create(""));
        if (Objects.equals(currentCertificate.getCertificate().getParentCertificateUri(), signingCertificateUri)) {
            return false;
        }
        log.info("Signing certificate uri has changed, requesting new certificate");
        return true;
    }

    private boolean newValidityTimeApplies(ValidityPeriod validityPeriod, OutgoingResourceCertificate currentCertificate) {
        if (currentCertificate.getValidityPeriod().getNotValidAfter().isBefore(validityPeriod.getNotValidAfter())) {
            log.info("Current certificate for resource class {} expires soon, requesting new certificate", DEFAULT_RESOURCE_CLASS);
            return true;
        } else if (validityPeriod.getNotValidBefore().isBefore(currentCertificate.getValidityPeriod().getNotValidBefore())) {
            log.warn("Requested validity period starts before current validity period of certificate, requesting new certificate. Did the clock change?");
            return true;
        }

        return false;
    }

    @Override
    public CertificateIssuanceResponse processCertificateIssuanceRequest(ChildCertificateAuthority requestingCa,
                                                                         CertificateIssuanceRequest request,
                                                                         ResourceCertificateRepository resourceCertificateRepository,
                                                                         DBComponent dbComponent,
                                                                         int issuedCertificatesPerSignedKeyLimit) {
        Validate.isTrue(isProductionCa() || isAllResourcesCa(), "Must be Production or 'All Resources' CA");
        validateChildResourceSet(request.getResources());
        int count = resourceCertificateRepository.countNonExpiredOutgoingCertificates(request.getSubjectPublicKey(), getCurrentKeyPair());
        if (count >= issuedCertificatesPerSignedKeyLimit) {
            throw new CertificationResourceLimitExceededException("number of issued certificates for public key exceeds the limit (" + count + " >= " + issuedCertificatesPerSignedKeyLimit + ")");
        }
        this.manifestAndCrlCheckNeeded = true;
        return getCurrentKeyPair().processCertificateIssuanceRequest(requestingCa, request, dbComponent.nextSerial(this), resourceCertificateRepository);
    }

    @Override
    public void processCertificateIssuanceResponse(CertificateIssuanceResponse response, ResourceCertificateRepository resourceCertificateRepository) {
        X509ResourceCertificate certificate = response.getCertificate();
        Optional<KeyPairEntity> keyPair = findKeyPairByPublicKey(certificate.getPublicKey());
        keyPair.ifPresent(kp -> updateIncomingResourceCertificate(kp, certificate, response.getPublicationUri()));
    }

    @VisibleForTesting
    void updateIncomingResourceCertificate(KeyPairEntity subjectKeyPair, X509ResourceCertificate certificate, URI publicationURI) {
        subjectKeyPair.updateIncomingResourceCertificate(certificate, publicationURI);

        if (getKeyPairs().size() == 1 && subjectKeyPair.isPending()) {
            activatePendingKey(subjectKeyPair, getVersionedId());
        }

        this.manifestAndCrlCheckNeeded = true;
    }

    private void activatePendingKey(KeyPairEntity newKeyPair, VersionedId versionedId) {
        Optional<KeyPairEntity> currentKeyPair = findCurrentKeyPair();
        currentKeyPair.ifPresent(KeyPairEntity::deactivate);
        newKeyPair.activate();
        HostedCertificateAuthority.EVENTS.publish(this, new KeyPairActivatedEvent(versionedId, newKeyPair.getName()));
    }

    /**
     * Let the CA initiate key rolls in its resource classes, if applicable, meaning:
     * there is only one, current, key in existence for the resource class and it's older than the maxAge (days) supplied.
     * Will result in a UpstreamCARequest if any new keys were created, and certificates requested for them.
     */
    public List<CertificateIssuanceRequest> initiateKeyRolls(int maxAge,
                                                             KeyPairService keyPairService,
                                                             CertificateRequestCreationService certificateRequestCreationService) {
        final CertificateIssuanceRequest request = certificateRequestCreationService.initiateKeyRoll(maxAge, keyPairService, this);
        return request == null ? Collections.emptyList() : Collections.singletonList(request);
    }

    /**
     * Let the CA activate any keys in any of its resource classes, that have been pending for the minStagingTime
     *
     * @return false if NO key was activated
     */
    public boolean activatePendingKeys(Duration minStagingTime) {
        final AtomicBoolean anyKeysActivated = new AtomicBoolean(false);
        findPendingKeyPair()
            .filter(pkp -> pkp.getStatusChangedAt(KeyPairStatus.PENDING).isBefore(new DateTime().minus(minStagingTime)))
            .ifPresent(pkp -> {
                findCurrentKeyPair().ifPresent(KeyPairEntity::deactivate);
                pkp.activate();
                EVENTS.publish(this, new KeyPairActivatedEvent(getVersionedId(), pkp.getName()));
                anyKeysActivated.set(true);
                this.manifestAndCrlCheckNeeded = true;
            });
        return anyKeysActivated.get();
    }

    /**
     * Let the CA request revocation of any old keys used by any of its resource classes
     */
    public List<CertificateRevocationRequest> requestOldKeysRevocation(ResourceCertificateRepository resourceCertificateRepository) {
        return getKeyPairs().stream()
            .filter(KeyPairEntity::isOld)
            .filter(kp -> !resourceCertificateRepository.existsCurrentOutgoingCertificatesExceptForManifest(kp))
            .map(kp -> new CertificateRevocationRequest(kp.getPublicKey()))
            .collect(Collectors.toList());
    }

    @Override
    public void processCertificateRevocationResponse(CertificateRevocationResponse response,
                                                     PublishedObjectRepository publishedObjectRepository,
                                                     KeyPairDeletionService keyPairDeletionService) {
        findKeyPairByPublicKey(response.getSubjectPublicKey()).ifPresent(keyPair -> {
            keyPair.deleteIncomingResourceCertificate();
            keyPair.requestRevoke();
            keyPair.revoke(publishedObjectRepository);

            keyPairDeletionService.deleteRevokedKeysFromResponses(this, Collections.singletonList(response));

            this.manifestAndCrlCheckNeeded = true;
        });
    }

    @Override
    public List<? extends CertificateProvisioningMessage> processResourceClassListResponse(
        ResourceClassListResponse response,
        CertificateRequestCreationService certificateRequestCreationService
    ) {
        Validate.isTrue(!isAllResourcesCa(), "Only Production and Customer CAs can do it.");

        IpResourceSet certifiableResources = response.getCertifiableResources();
        if (certifiableResources.isEmpty()) {
            // No certifiable resources, revoke any existing certificates.
            return certificateRequestCreationService.createCertificateRevocationRequestForAllKeys(this);
        }

        return certificateRequestCreationService
            .createCertificateIssuanceRequestForAllKeys(this, certifiableResources);
    }

    @Override
    public CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                             ResourceCertificateRepository resourceCertificateRepository) {
        Validate.isTrue(hasCurrentKeyPair(), "Must have current key pair to revoke child certificates");
        this.manifestAndCrlCheckNeeded = true;
        return getCurrentKeyPair().processCertificateRevocationRequest(request, resourceCertificateRepository);
    }

    @Override
    public ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery query) {
        final IpResourceSet certifiedResources = new IpResourceSet(getCertifiedResources());
        certifiedResources.retainAll(query.getResources());
        return new ResourceClassListResponse(certifiedResources);
    }

    public boolean hasCurrentKeyPair() {
        return hasKeyPairWithStatus(KeyPairStatus.CURRENT);
    }

    public boolean hasRollInProgress() {
        return hasKeyPairWithStatus(KeyPairStatus.NEW, KeyPairStatus.PENDING, KeyPairStatus.OLD);
    }

    private boolean hasKeyPairWithStatus(KeyPairStatus... status) {
        return keyPairs.stream().anyMatch(keyPair ->
            Arrays.stream(status).anyMatch(s -> s.equals(keyPair.getStatus()))
        );
    }

    public boolean currentKeyPairIsOlder(int ageDays) {
        return findCurrentKeyPair().map(keyPairEntity -> {
            final DateTime maxCreationTime = new DateTime().minusDays(ageDays);
            return keyPairEntity.getCreatedAt().isBefore(maxCreationTime);
        }).orElse(false);
    }

    public Optional<KeyPairEntity> findPendingKeyPair() {
        return findFirstKeyPairWithStatus(KeyPairStatus.PENDING);
    }

    public Optional<KeyPairEntity> findOldKeyPair() {
        return findFirstKeyPairWithStatus(KeyPairStatus.OLD);
    }

    public KeyPairEntity createNewKeyPair(KeyPairService keyPairService) {
        final Set<String> uniqueNames = keyPairs.stream()
            .map(KeyPairEntity::getName)
            .collect(Collectors.toSet());

        String newKeyPairName;
        do {
            newKeyPairName = getId() + "-" + DEFAULT_RESOURCE_CLASS + "-" + UUID.randomUUID();
        } while(uniqueNames.contains(newKeyPairName));

        KeyPairEntity keyPair = keyPairService.createKeyPairEntity(newKeyPairName);
        keyPairs.add(keyPair);
        return keyPair;
    }

    public void addKeyPair(KeyPairEntity keyPair) {
        if (findKeyPairByName(keyPair.getName()).isPresent()) {
            throw new NameNotUniqueException(keyPair.getName());
        }
        keyPairs.add(keyPair);
    }

    public IncomingResourceCertificate getCurrentIncomingCertificate() {
        return findCurrentIncomingResourceCertificate().orElse(null);
    }

    public Optional<IncomingResourceCertificate> findCurrentIncomingResourceCertificate() {
        return findCurrentKeyPair().flatMap(KeyPairEntity::findCurrentIncomingCertificate);
    }
}
