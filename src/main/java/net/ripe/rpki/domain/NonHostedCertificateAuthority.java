package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateProvisioningMessage;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@DiscriminatorValue(value = "NONHOSTED")
@Slf4j
public class NonHostedCertificateAuthority extends CertificateAuthority {

    /**
     * Maximum number of public keys for a single non-hosted CA. A public key is removed once all its signing
     * certificates have expired.
     */
    public static final int PUBLIC_KEY_LIMIT = 20;

    /**
     * Maximum number of certificates issued by the parent for each public key. The limit only applies when the
     * non-hosted CA performs an issuance request using the up-down protocol. The parent can always issue a new
     * certificate, even when this limit is exceeded. This ensures registry resource changes are reflected on
     * the issued certificates.
     *
     * Note that this limit is present to avoid non-hosted CAs asking for many certificates, each with different
     * resources or SIA information, for the same key. Having too many certificates per key will result in bad
     * JPA performance and can cause other Denial-of-Service attacks on the current core code.
     */
    public static final int INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT = 1000;

    @NotNull
    @Column(name = "identity_certificate")
    private String identityCertificate;

    @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinColumn(name = "ca_id", nullable = false)
    private Set<PublicKeyEntity> publicKeys = new HashSet<>();

    @Getter
    @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinColumn(name = "ca_id", nullable = false)
    private Collection<NonHostedPublisherRepository> publisherRepositories = new ArrayList<>();

    protected NonHostedCertificateAuthority() {
    }

    public NonHostedCertificateAuthority(long id, X500Principal name,
                                         ProvisioningIdentityCertificate identityCertificate,
                                         ParentCertificateAuthority parent) {
        super(id, parent, name);
        this.identityCertificate = Base64.encodeBase64URLSafeString(identityCertificate.getEncoded());
    }

    public ProvisioningIdentityCertificate getProvisioningIdentityCertificate() {
        ProvisioningIdentityCertificateParser certificateParser = new ProvisioningIdentityCertificateParser();
        certificateParser.parse("id-cert", Base64.decodeBase64(identityCertificate));
        return certificateParser.getCertificate();
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.NONHOSTED;
    }

    @Override
    public Optional<IpResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return Optional.of(resourceLookupService.lookupMemberCaPotentialResources(getName()));
    }

    @Override
    public NonHostedCertificateAuthorityData toData() {
        final IpResourceSet resources = new IpResourceSet();
        Set<NonHostedPublicKeyData> publicKeyData = getPublicKeys().stream().map(PublicKeyEntity::toData).collect(Collectors.toSet());
        for (NonHostedPublicKeyData publicKeyDatum : publicKeyData) {
            if (publicKeyDatum.getCurrentCertificate() != null) {
                resources.addAll(publicKeyDatum.getCurrentCertificate().getCertificate().getResources());
            }
        }

        return new NonHostedCertificateAuthorityData(
            getVersionedId(),
            getName(),
            getUuid(),
            getParent().getId(),
            getProvisioningIdentityCertificate(),
            resources,
            publicKeyData
        );
    }

    public Collection<PublicKeyEntity> getPublicKeys() {
        return Collections.unmodifiableCollection(publicKeys);
    }

    public Optional<PublicKeyEntity> findPublicKeyEntityByPublicKey(PublicKey publicKey) {
        String encodedPublicKey = KeyPairUtil.getEncodedKeyIdentifier(publicKey);
        return getPublicKeys()
            .stream()
            .filter(publicKeyEntity -> {
                String encodedStoredPublicKey = KeyPairUtil.getEncodedKeyIdentifier(publicKeyEntity.getPublicKey());
                return encodedStoredPublicKey.equals(encodedPublicKey);
            }).findFirst();
    }

    public PublicKeyEntity findOrCreatePublicKeyEntityByPublicKey(PublicKey publicKey) {
        return findPublicKeyEntityByPublicKey(publicKey).orElseGet(() -> {
            if (publicKeys.size() >= PUBLIC_KEY_LIMIT) {
                throw new CertificationResourceLimitExceededException("maximum number of public keys exceeded (" + publicKeys.size() + " > " + PUBLIC_KEY_LIMIT + ")");
            }
            PublicKeyEntity keyPair = new PublicKeyEntity(publicKey);
            publicKeys.add(keyPair);
            return keyPair;
        });
    }

    @Override
    public void processCertificateIssuanceResponse(CertificateIssuanceResponse response, ResourceCertificateRepository resourceCertificateRepository) {
        // HACK to find the outgoing resource certificate. We should refactor this so that we have a reference to an IncomingResourceCertificate instead
        // of directly referencing the outgoing resource certificate, just like KeyPairEntity.
        Collection<OutgoingResourceCertificate> currentCertificatesBySubjectPublicKey = resourceCertificateRepository.findCurrentCertificatesBySubjectPublicKey(response.getCertificate().getPublicKey());
        PublicKeyEntity publicKey = findOrCreatePublicKeyEntityByPublicKey(response.getCertificate().getPublicKey());
        currentCertificatesBySubjectPublicKey.forEach(publicKey::addOutgoingResourceCertificate);
    }

    @Override
    public void processCertificateRevocationResponse(CertificateRevocationResponse response, PublishedObjectRepository publishedObjectRepository, KeyPairDeletionService keyPairDeletionService) {
        // Nothing to do for now, as the resource certificate is already revoked by the parent CA before we get here.
        // We could mark the public key as revoked, but there is nothing in RFC6492 to indicate that should happen
        // after a certificate revocation request.
    }

    @Override
    public List<? extends CertificateProvisioningMessage> processResourceClassListResponse(ResourceClassListResponse response, KeyPairService keyPairService, CertificateRequestCreationService certificateRequestCreationService) {
        return publicKeys.stream()
            .flatMap(pk -> {
                IpResourceSet certifiableResources = response.getCertifiableResources();
                IpResourceSet certificateResources = pk.getRequestedResourceSets().calculateEffectiveResources(certifiableResources);
                if (pk.isRevoked() || certificateResources.isEmpty()) {
                    return Stream.of(new CertificateRevocationRequest(pk.getPublicKey()));
                }

                return Stream.of(new CertificateIssuanceRequest(
                    certificateResources,
                    pk.getSubjectForCertificateRequest(),
                    pk.getPublicKey(),
                    pk.getRequestedSia().toArray(new X509CertificateInformationAccessDescriptor[0]))
                );
            })
            .collect(Collectors.toList());
    }

    public void addNonHostedPublisherRepository(UUID publisherHandle, PublisherRequest publisherRequest, RepositoryResponse repositoryResponse) {
        Validate.isTrue(
            publisherRepositories.stream().noneMatch(repository -> publisherHandle.equals(repository.getPublisherHandle())),
            "publisher_handle must be unique"
        );
        publisherRepositories.add(new NonHostedPublisherRepository(publisherHandle, publisherRequest, repositoryResponse));
    }

    public boolean removeNonHostedPublisherRepository(UUID publisherHandle) {
        return publisherRepositories.removeIf(repository -> publisherHandle.equals(repository.getPublisherHandle()));
    }
}
