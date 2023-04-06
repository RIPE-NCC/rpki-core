package net.ripe.rpki.domain.crl;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.crl.X509CrlBuilder;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collection;

import static net.ripe.rpki.domain.manifest.ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT;

@Entity
@Table(name = "crlentity")
@SequenceGenerator(name = "seq_crlentity", sequenceName = "seq_all", allocationSize = 1)
public class CrlEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_crlentity")
    private Long id;

    @Column(name = "nextnumber", nullable = false)
    private long nextNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "keypair_id", nullable = false)
    private KeyPairEntity keyPair;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST}, fetch = FetchType.LAZY)
    @JoinColumn(name = "published_object_id", nullable = false)
    private PublishedObject publishedObject;


    protected CrlEntity() {
    }

    public CrlEntity(KeyPairEntity keyPair) {
        this.keyPair = keyPair;
        this.nextNumber = 1L;
    }

    @Override
    public Long getId() {
        return id;
    }

    long getNextNumber() {
        return nextNumber;
    }

    public synchronized long getAndIncrementNextNumber() {
        return nextNumber++;
    }

    public byte[] getEncoded() {
        return publishedObject == null ? null : publishedObject.getContent();
    }

    public X509Crl getCrl() {
        return publishedObject == null ? null : new X509Crl(getEncoded());
    }

    public KeyPairEntity getKeyPair() {
        return keyPair;
    }

    public PublishedObject getPublishedObject() {
        return publishedObject;
    }

    public void setPublishedObject(PublishedObject publishedObject) {
        this.publishedObject = publishedObject;
    }

    public void withdraw() {
        if (publishedObject != null) {
            publishedObject.withdraw();
        }
    }

    public boolean isUpdateNeeded(DateTime now, ResourceCertificateRepository resourceCertificateRepository) {
        X509Crl current = getCrl();
        if (current == null) {
            return true;
        }

        if (current.getNextUpdateTime().minus(TIME_TO_NEXT_UPDATE_SOFT_LIMIT).isBefore(now)) {
            return true;
        }

        if (isPublicationDirChanged(keyPair.getCertificateRepositoryLocation())) {
            return true;
        }

        Collection<OutgoingResourceCertificate> revokedCertificates = resourceCertificateRepository.findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(keyPair, now);
        X509CrlBuilder builder = newCrlBuilderWithEntries(revokedCertificates);
        return !builder.isSatisfiedByEntries(current);
    }

    private boolean isPublicationDirChanged(URI resourceCertificateRepository) {
        if (getPublishedObject() == null) {
            return true;
        }
        String oldDirectory = StringUtils.removeEnd(getPublishedObject().getDirectory(), "/");
        String newDirectory = StringUtils.removeEnd(resourceCertificateRepository.toString(), "/");
        return !oldDirectory.equals(newDirectory);
    }

    public void update(ValidityPeriod validityPeriod, ResourceCertificateRepository resourceCertificateRepository) {
        Collection<OutgoingResourceCertificate> revokedCertificates = resourceCertificateRepository.findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(keyPair, validityPeriod.getNotValidBefore());
        X509CrlBuilder builder = newCrlBuilderWithEntries(revokedCertificates);
        builder.withAuthorityKeyIdentifier(keyPair.getPublicKey());
        builder.withIssuerDN(keyPair.getCurrentIncomingCertificate().getSubject());
        builder.withThisUpdateTime(validityPeriod.getNotValidBefore());
        builder.withNextUpdateTime(validityPeriod.getNotValidAfter());
        builder.withNumber(BigInteger.valueOf(getAndIncrementNextNumber()));
        builder.withSignatureProvider(keyPair.getSignatureProvider());

        byte[] encoded = builder.build(keyPair.getPrivateKey()).getEncoded();
        withdraw();

        setPublishedObject(new PublishedObject(
                keyPair, keyPair.getCrlFilename(), encoded, true, keyPair.getCertificateRepositoryLocation(), validityPeriod));
    }

    private X509CrlBuilder newCrlBuilderWithEntries(Collection<OutgoingResourceCertificate> revokedCertificates) {
        X509CrlBuilder builder = new X509CrlBuilder();
        for (OutgoingResourceCertificate certificate : revokedCertificates) {
            builder.addEntry(certificate.getSerial(), certificate.getRevocationTime());
        }

        return builder;
    }

}
