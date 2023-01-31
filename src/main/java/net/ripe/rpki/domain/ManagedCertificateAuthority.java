package net.ripe.rpki.domain;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.events.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateProvisioningMessage;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.ripencc.support.event.DefaultEventDelegate;
import net.ripe.rpki.ripencc.support.event.EventDelegate;
import net.ripe.rpki.ripencc.support.event.EventSubscription;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import javax.persistence.*;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.v;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


/**
 * Managed Certificate Authorities have their key pairs stored locally, as opposed to delegated/non-hosted certificate
 * authorities where the private key is stored on the client's servers.
 *
 * Managed certificate authorities publish a manifest and CRL for every active key pair.
 */
@Entity
@Slf4j
public abstract class ManagedCertificateAuthority extends CertificateAuthority implements ParentCertificateAuthority {

    public static final EventDelegate<CertificateAuthorityEvent> EVENTS = new DefaultEventDelegate<>();

    public static EventSubscription subscribe(final CertificateAuthorityEventVisitor listener, CommandContext recording) {
        return EVENTS.subscribe(event -> event.accept(listener, recording));
    }

    @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL )
    @JoinColumn(name = "ca_id", nullable = false)
    private Set<KeyPairEntity> keyPairs = new HashSet<>();

    /**
     * The last time the ASPA or ROA configuration was updated. This can bever be equal to {@link #configurationAppliedAt}.
     */
    @Column(name = "configuration_updated_at")
    @NonNull
    private DateTime configurationUpdatedAt = UTC.dateTime();

    /**
     * The last time the ASPA and ROA configuration were applied. This can never be equal to {@link #configurationUpdatedAt}.
     */
    @Column(name = "configuration_applied_at")
    @NonNull
    private DateTime configurationAppliedAt = this.configurationUpdatedAt.plusMillis(1);

    protected ManagedCertificateAuthority() {
    }

    protected ManagedCertificateAuthority(long id, X500Principal name, UUID uuid, ParentCertificateAuthority parent) {
        super(id, name, uuid, parent);
    }

    public UpStreamCARequestEntity getUpStreamCARequestEntity() {
        return null;
    }

    @Override
    public Optional<ManagedCertificateAuthority> asManagedCertificateAuthority() {
        return Optional.of(this);
    }

    @Override
    public ManagedCertificateAuthorityData toData() {
        TrustAnchorRequest upStreamCARequest = getUpStreamCARequestEntity() != null ? getUpStreamCARequestEntity().getUpStreamCARequest() : null;

        final List<KeyPairData> keys = getKeyPairs().stream()
            .map(KeyPairEntity::toData)
            .collect(Collectors.toList());

        return new ManagedCertificateAuthorityData(
            getVersionedId(), getName(), getUuid(),
            getParent() == null ? null : getParent().getId(),
            getType(),
            getCertifiedResources(), upStreamCARequest, keys);
    }

    public void removeKeyPair(final KeyPairEntity keyPair) {
        Validate.isTrue(keyPair.isRemovable(), "Key pair is in use");
        keyPairs.remove(keyPair);
    }

    @Override
    public Collection<PublicKey> getSignedPublicKeys() {
        return keyPairs.stream().map(KeyPairEntity::getPublicKey).collect(Collectors.toList());
    }

    public Collection<KeyPairEntity> getKeyPairs() {
        return keyPairs;
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

    public ImmutableResourceSet getCertifiedResources() {
        return findCurrentKeyPair()
            .flatMap(KeyPairEntity::findCurrentIncomingCertificate)
            .map(ResourceCertificate::getResources)
            .orElse(ImmutableResourceSet.empty());
    }

    public KeyPairEntity getCurrentKeyPair() {
        return findCurrentKeyPair().orElseThrow(() -> new CertificateAuthorityException("No active key pair available to sign requested certificate"));
    }

    public void validateChildResourceSet(ImmutableResourceSet childResources) {
        ImmutableResourceSet parentResources = getCertifiedResources();
        if (!parentResources.contains(childResources)) {
            ImmutableResourceSet bad = childResources.difference(parentResources);
            throw new CertificateAuthorityException("child resources '" + bad + "' are not contained in parent resources");
        }
    }

    public boolean isConfigurationCheckNeeded() {
        return this.configurationUpdatedAt.isAfter(this.configurationAppliedAt);
    }

    public void markConfigurationUpdated() {
        this.configurationUpdatedAt = UTC.dateTime();
        if (!this.configurationUpdatedAt.isAfter(this.configurationAppliedAt)) {
            // Clock skew? Ensure updated_at is always after applied_at.
            this.configurationUpdatedAt = this.configurationAppliedAt.plusMillis(1);
        }
        Validate.isTrue(this.configurationUpdatedAt.isAfter(this.configurationAppliedAt), "post-condition");
    }

    public void markConfigurationApplied() {
        this.configurationAppliedAt = UTC.dateTime();
        if (!this.configurationAppliedAt.isAfter(this.configurationUpdatedAt)) {
            // Clock skew? Ensure applied_at is always after updated_at.
            this.configurationAppliedAt = this.configurationUpdatedAt.plusMillis(1);
        }
        Validate.isTrue(this.configurationAppliedAt.isAfter(this.configurationUpdatedAt), "post-condition");
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("id", getId())
            .append("type", getType())
            .append("name", getName())
            .toString();
    }

    public Optional<KeyPairData> deleteRevokedKey(String encodedSKI, Consumer<KeyPairEntity> onDelete) {
        final Optional<KeyPairEntity> keyPair = findKeyPairByEncodedPublicKey(encodedSKI);
        keyPair.ifPresent(kp -> {
            Validate.isTrue(kp.isRevoked(), "Can only archive revoked key");
            onDelete.accept(kp);
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

        if (log.isInfoEnabled()) {
            final ImmutableResourceSet added = request.getResources().difference(latestOutgoingCertificate.getResources());
            final ImmutableResourceSet removed = latestOutgoingCertificate.getResources().difference(request.getResources());

            log.info(
                    "Current certificate for resource class {} of {} has different resources. Added resources: {}, removed resources: {}",
                    DEFAULT_RESOURCE_CLASS, v("subject", request.getSubjectDN()),
                    v("addedResources", added), v("removedResources", removed),
                    v("currentResources", latestOutgoingCertificate.getResources()), v("requestedResources", request.getResources())
            );
        }
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
                                                                         int issuedCertificatesPerSignedKeyLimit) {
        Validate.isTrue(isProductionCa() || isAllResourcesCa(), "Must be Production or 'All Resources' CA");
        validateChildResourceSet(request.getResources());
        int count = resourceCertificateRepository.countNonExpiredOutgoingCertificates(request.getSubjectPublicKey(), getCurrentKeyPair());
        if (count >= issuedCertificatesPerSignedKeyLimit) {
            throw new CertificationResourceLimitExceededException("number of issued certificates for public key exceeds the limit (" + count + " >= " + issuedCertificatesPerSignedKeyLimit + ")");
        }
        return getCurrentKeyPair().processCertificateIssuanceRequest(requestingCa, request, SerialNumberSupplier.getInstance().get(), resourceCertificateRepository);
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
            activatePendingKey(subjectKeyPair);
        }

        // status can change after activatePendingKey
        if (subjectKeyPair.isCurrent()) {
            ManagedCertificateAuthority.EVENTS.publish(this, new IncomingCertificateUpdatedEvent(getVersionedId(), certificate));
        }
    }

    private void activatePendingKey(KeyPairEntity newKeyPair) {
        Optional<KeyPairEntity> currentKeyPair = findCurrentKeyPair();
        currentKeyPair.ifPresent(KeyPairEntity::deactivate);
        newKeyPair.activate();
        ManagedCertificateAuthority.EVENTS.publish(this, new KeyPairActivatedEvent(getVersionedId(), newKeyPair));
    }

    /**
     * Let the CA initiate key rolls in its resource classes, if applicable, meaning:
     * there is only one, current, key in existence for the resource class and it's older than the maxAge (days) supplied.
     * Will result in a UpstreamCARequest if any new keys were created, and certificates requested for them.
     */
    public List<CertificateIssuanceRequest> initiateKeyRolls(int maxAge,
                                                             CertificateRequestCreationService certificateRequestCreationService) {
        final Optional<CertificateIssuanceRequest> request = certificateRequestCreationService.initiateKeyRoll(this, maxAge);
        return request.map(Collections::singletonList).orElse(Collections.emptyList());
    }

    /**
     * Let the CA activate any keys in any of its resource classes, that have been pending for the minStagingTime
     *
     * @return false if NO key was activated
     */
    public boolean activatePendingKeys(Duration minStagingTime) {
        return findPendingKeyPair()
            .filter(pkp -> pkp.getStatusChangedAt(KeyPairStatus.PENDING).isBefore(new DateTime().minus(minStagingTime)))
            .map(pkp -> {
                activatePendingKey(pkp);
                return true;
            })
            .orElse(false);
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
            if (keyPair.isCurrent()) {
                keyPair.findCurrentIncomingCertificate().ifPresent(incomingResourceCertificate -> {
                    log.info(
                        "[ca={}] incoming certificate for CURRENT key revoked serial={} uri={} resources before revocation={}",
                        getId(),
                        incomingResourceCertificate.getCertificate(),
                        incomingResourceCertificate.getPublicationUri(),
                        incomingResourceCertificate.getResources()
                    );
                    ManagedCertificateAuthority.EVENTS.publish(this, new IncomingCertificateRevokedEvent(
                        getVersionedId(),
                        response,
                        incomingResourceCertificate.getPublicationUri(),
                        incomingResourceCertificate.getCertificate())
                    );
                });
            }

            keyPair.deleteIncomingResourceCertificate();
            keyPair.requestRevoke();
            keyPair.revoke(publishedObjectRepository);

            keyPairDeletionService.deleteRevokedKeysFromResponses(this, Collections.singletonList(response));
        });
    }

    @Override
    public List<? extends CertificateProvisioningMessage> processResourceClassListResponse(
        ResourceClassListResponse response,
        CertificateRequestCreationService certificateRequestCreationService
    ) {
        Validate.isTrue(!isAllResourcesCa(), "Only Production and Hosted CAs can do it.");

        ImmutableResourceSet certifiableResources = response.getCertifiableResources();
        if (certifiableResources.isEmpty()) {
            // No certifiable resources, revoke any existing certificates.
            return certificateRequestCreationService.createCertificateRevocationRequestForAllKeys(this);
        }

        if (keyPairs.isEmpty()) {
            // No key pairs (probable removed by previously not having any resources, see above), so create a new
            // key and request a certificate.
            return Collections.singletonList(
                certificateRequestCreationService.createCertificateIssuanceRequestForNewKeyPair(this, certifiableResources)
            );
        } else {
            return certificateRequestCreationService
                .createCertificateIssuanceRequestForAllKeys(this, certifiableResources);
        }
    }

    @Override
    public CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                             ResourceCertificateRepository resourceCertificateRepository) {
        Validate.isTrue(hasCurrentKeyPair(), "Must have current key pair to revoke child certificates");
        return getCurrentKeyPair().processCertificateRevocationRequest(request, resourceCertificateRepository);
    }

    @Override
    public ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery query) {
        final ImmutableResourceSet certifiedResources = getCertifiedResources().intersection(query.getResources());
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
        KeyPairEntity keyPair = keyPairService.createKeyPairEntity();
        keyPairs.add(keyPair);
        return keyPair;
    }

    public void addKeyPair(KeyPairEntity keyPair) {
        keyPairs.add(keyPair);
    }

    @VisibleForTesting
    public IncomingResourceCertificate getCurrentIncomingCertificate() {
        return findCurrentIncomingResourceCertificate().orElse(null);
    }

    public Optional<IncomingResourceCertificate> findCurrentIncomingResourceCertificate() {
        return findCurrentKeyPair().flatMap(KeyPairEntity::findCurrentIncomingCertificate);
    }
}
