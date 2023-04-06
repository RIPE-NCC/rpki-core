package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.signing.ChildCertificateSigner;
import net.ripe.rpki.hsm.Keys;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.stream.Collectors;

import static net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil.extractPublicationDirectory;

/**
 * A KeyPair belongs to a single Certificate Authority and is a holder for a private and a public key.
 * A parent key pair can act as a Trusted Third Party to verify the public key of the child key pairs.
 * <p>
 * The public key of a KeyPair is used by relying parties to verify the certificates issues by this Certificate Authority.
 * The private key of a KeyPair is used to sign the issued certificates.
 * <p>
 * A KeyPair is a link in a chain to a trust anchor (Trusted Third Party).
 * The key pair is part of the {@link ManagedCertificateAuthority} aggregate.
 */
@Entity
@Table(name = "keypair")
@SequenceGenerator(name = "seq_keypair", sequenceName = "seq_all", allocationSize = 1)
public class KeyPairEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_keypair")
    private Long id;

    @NotNull
    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private KeyPairStatus status;

    @OneToOne(mappedBy = "subjectKeyPair", orphanRemoval = true, cascade = CascadeType.ALL)
    private IncomingResourceCertificate incomingResourceCertificate;

    @ElementCollection
    @CollectionTable(name = "keypair_statushistory", joinColumns = @JoinColumn(name = "keypair_id"))
    private List<KeyPairStatusHistory> statusHistory = new ArrayList<>();

    @Column(nullable = false)
    private int size;

    @NotNull
    @Column(nullable = false)
    private String algorithm = KeyPairFactory.ALGORITHM;

    @Embedded
    private PersistedKeyPair persistedKeyPair;

    @NotNull
    @Column(name = "crl_filename")
    private String crlFilename;

    @NotNull
    @Column(name = "manifest_filename")
    private String manifestFilename;

    protected KeyPairEntity() {
        setStatus(KeyPairStatus.PENDING);
    }

    public KeyPairEntity(KeyPair keyPair,
                         KeyPairEntitySignInfo signInfo,
                         String crlFilename,
                         String manifestFilename) {
        this();
        this.size = ((RSAPublicKey) keyPair.getPublic()).getModulus().bitLength();
        this.persistedKeyPair = new PersistedKeyPair(keyPair, signInfo);
        this.crlFilename = crlFilename;
        this.manifestFilename = manifestFilename;
        assertValid();
    }

    @Override
    public Long getId() {
        return id;
    }

    public KeyPairStatus getStatus() {
        return status;
    }

    public DateTime getCreationDate() {
        return new DateTime(getCreatedAt(), DateTimeZone.UTC);
    }

    // Key Pair Life Cycle support

    public boolean isPending() {
        return status == KeyPairStatus.PENDING;
    }

    public boolean isCurrent() {
        return status == KeyPairStatus.CURRENT;
    }

    public boolean isOld() {
        return status == KeyPairStatus.OLD;
    }

    public boolean isPublishable() {
        return findCurrentIncomingCertificate().isPresent();
    }

    public void activate() {
        Validate.isTrue(status == KeyPairStatus.PENDING, "only PENDING keys can become CURRENT");
        Validate.isTrue(findCurrentIncomingCertificate().isPresent(), "only key with CURRENT certificate can be activated");
        setStatus(KeyPairStatus.CURRENT);
    }

    public void deactivate() {
        Validate.isTrue(status == KeyPairStatus.CURRENT, "only CURRENT keys can be deactivated");
        setStatus(KeyPairStatus.OLD);
    }

    public void revoke(KeyPairDeletionService keyPairDeletionService) {
        deleteIncomingResourceCertificate();
        keyPairDeletionService.deleteRevokedKey(this);
    }

    public boolean isRemovable() {
        return incomingResourceCertificate == null;
    }

    public DateTime getStatusChangedAt(KeyPairStatus status) {
        return statusHistory.stream()
            .filter(change -> change.getStatus() == status)
            .findFirst()
            .map(KeyPairStatusHistory::getChangedAt)
            .orElse(null);
    }

    public void deleteIncomingResourceCertificate() {
        incomingResourceCertificate = null;
    }

    public void updateIncomingResourceCertificate(X509ResourceCertificate certificate, URI publicationURI) {
        if (this.incomingResourceCertificate == null) {
            this.incomingResourceCertificate = new IncomingResourceCertificate(certificate, publicationURI, this);
        } else {
            this.incomingResourceCertificate.update(certificate, publicationURI);
        }
    }

    public Optional<IncomingResourceCertificate> findCurrentIncomingCertificate() {
        return Optional.ofNullable(incomingResourceCertificate);
    }

    public IncomingResourceCertificate getCurrentIncomingCertificate() {
        Validate.notNull(incomingResourceCertificate, "no current incoming certificate for key pair " + this);
        return incomingResourceCertificate;
    }

    public Map<KeyPairStatus, DateTime> getStatusChangeTimestamps() {
        return statusHistory.stream().collect(Collectors.toMap(
                KeyPairStatusHistory::getStatus,
                KeyPairStatusHistory::getChangedAt, (a, b) -> b, TreeMap::new));
    }

    private void setStatus(KeyPairStatus status) {
        this.status = status;
        this.statusHistory.add(new KeyPairStatusHistory(status, new DateTime(DateTimeZone.UTC)));
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("id", getId());
        builder.append("status", getStatus());
        builder.append("algorithm", algorithm);
        builder.append("size", size);
        return builder.toString();
    }

    public KeyPair getKeyPair() {
        return persistedKeyPair.getKeyPair();
    }

    public PublicKey getPublicKey() {
        return persistedKeyPair.getPublicKey();
    }

    public PrivateKey getPrivateKey() {
        return persistedKeyPair.getPrivateKey();
    }

    public String getEncodedKeyIdentifier() {
        return persistedKeyPair.getEncodedKeyIdentifier();
    }

    public String getSignatureProvider() {
        return persistedKeyPair.getSignatureProvider();
    }

    public String getKeyStoreString() {
        return persistedKeyPair.getKeyStoreString();
    }

    public URI getCertificateRepositoryLocation() {
        IncomingResourceCertificate currentIncomingCertificate = getCurrentIncomingCertificate();
        return extractPublicationDirectory(currentIncomingCertificate.getSia());
    }

    public String getManifestFilename() {
        return manifestFilename;
    }

    public String getCrlFilename() {
        return crlFilename;
    }

    public URI crlLocationUri() {
        return getCertificateRepositoryLocation().resolve(crlFilename);
    }

    public KeyPairData toData() {
        Optional<IncomingResourceCertificate> currentIncomingCertificate = findCurrentIncomingCertificate();
        return new KeyPairData(
            getId(),
            getKeyStoreString(),
            getStatus(),
            getCreationDate(),
            getStatusChangeTimestamps(),
            currentIncomingCertificate.map(c -> extractPublicationDirectory(c.getSia())).orElse(null),
            getCrlFilename(), getManifestFilename(),
            Keys.get().isDbProvider(persistedKeyPair.getKeyStoreProviderString()));
    }

    public CertificateIssuanceResponse processCertificateIssuanceRequest(ChildCertificateAuthority requestingCa,
                                                                         CertificateIssuanceRequest request,
                                                                         BigInteger serial,
                                                                         ResourceCertificateRepository resourceCertificateRepository) {
        DateTime now = new DateTime(DateTimeZone.UTC);
        ValidityPeriod validityPeriod = new ValidityPeriod(now, CertificateAuthority.calculateValidityNotAfter(now));
        revokeOldCertificates(request.getSubjectPublicKey(), resourceCertificateRepository);
        ChildCertificateSigner signer = new ChildCertificateSigner();
        OutgoingResourceCertificate outgoingResourceCertificate = signer.buildOutgoingResourceCertificate(request, validityPeriod, this, serial);
        outgoingResourceCertificate.setRequestingCertificateAuthority(requestingCa);
        resourceCertificateRepository.add(outgoingResourceCertificate);
        return new CertificateIssuanceResponse(outgoingResourceCertificate.getCertificate(), outgoingResourceCertificate.getPublicationUri());
    }

    private void revokeOldCertificates(PublicKey subjectPublicKey, ResourceCertificateRepository resourceCertificateRepository) {
        resourceCertificateRepository
                .findCurrentCertificatesBySubjectPublicKey(subjectPublicKey)
                .forEach(OutgoingResourceCertificate::revoke);
    }

    // TODO: Revoke certificates by subject public key hash
    public CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                             ResourceCertificateRepository resourceCertificateRepository) {
        PublicKey subjectPublicKey = request.getSubjectPublicKey();
        revokeOldCertificates(subjectPublicKey, resourceCertificateRepository);
        return new CertificateRevocationResponse(subjectPublicKey);
    }

}
