package net.ripe.rpki.domain.manifest;

import jakarta.persistence.*;
import lombok.Getter;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsBuilder;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.joda.time.DateTime;
import org.joda.time.Period;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Map;


/**
 * Entity for managing Manifests. In our model each KeyPairEntity is
 * associated with its own CA and has its own <b>unique</b> publication point.
 * The manifest is unique to such a publication point and describes its content.<br />
 * <br />
 * More info may be found here:<br />
 * <a href="http://tools.ietf.org/html/draft-ietf-sidr-rpki-manifests-02">
 * http://tools.ietf.org/html/draft-ietf-sidr-rpki-manifests-02
 * </a>
 */
@Entity
@Table(name = "manifestentity")
@SequenceGenerator(name = "seq_manifestentity", sequenceName = "seq_all", allocationSize=1)
public class ManifestEntity extends EntitySupport {
    /**
     * Manifests and CRLs are replaced as soon as the time to next update is less than this soft limit, but replacement
     * is spread out over time to reduce the load on the system and avoid spikes where we would suddenly republish the
     * majority of CRLs and manifests every 8 hours.
     */
    public static final Period TIME_TO_NEXT_UPDATE_SOFT_LIMIT = Period.hours(16);

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_manifestentity")
    private Long id;

    @Column(name = "nextnumber", nullable = false)
    private long nextNumber;

    /**
     * Does the manifest need to be re-issued right now?
     */
    @Column(name = "needs_reissuance", nullable = false)
    private boolean needsReissuance = false;

    @Getter
    @ManyToOne(optional = false)
    @JoinColumn(name = "keypair_id", nullable = false)
    private KeyPairEntity keyPair;

    @Getter
    @ManyToOne(optional = false, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "certificate_id", nullable = false)
    private OutgoingResourceCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST}, fetch = FetchType.LAZY)
    @JoinColumn(name = "published_object_id", nullable = false)
    private PublishedObject publishedObject;

    protected ManifestEntity() {}

    public ManifestEntity(KeyPairEntity keyPair) {
        this.keyPair = keyPair;
        this.nextNumber = 1L;
    }

    public byte[] getEncoded() {
        return publishedObject == null ? null : publishedObject.getContent();
    }

    public ManifestCms getManifestCms() {
        if (publishedObject == null) {
            return null;
        }

        ManifestCmsParser parser = new ManifestCmsParser();
        parser.parse("manifest", publishedObject.getContent());
        return parser.getManifestCms();
    }

    public boolean isUpdateNeeded(DateTime now, Map<String, Sha256> newManifestHashes) {
        ManifestCms cms = getManifestCms();
        return cms == null
                || isCloseToNextUpdateTime(now, cms)
                || parentCertificatePublicationLocationChanged(cms, keyPair.getCurrentIncomingCertificate())
                || !sameEntries(cms, newManifestHashes)
                || needsReissuance;
    }

    public boolean sameEntries(ManifestCms cms, Map<String, Sha256> newManifestHashes) {
        Map<String, byte[]> existingHashes = cms.getHashes();
        if (existingHashes.size() != newManifestHashes.size()) {
            return false;
        }
        for (Map.Entry<String, Sha256> entry : newManifestHashes.entrySet()) {
            byte[] existing = existingHashes.get(entry.getKey());
            if (existing == null || !entry.getValue().sameAs(existing)) {
                return false;
            }
        }
        return true;
    }

    public void update(OutgoingResourceCertificate eeCertificate,
                       KeyPair eeCertificateKeyPair,
                       String signatureProvider,
                       Map<String, Sha256>  manifestEntries) {
        withdraw();

        this.certificate = eeCertificate;

        ManifestCms manifestCms = buildManifestCms(manifestEntries, eeCertificateKeyPair, signatureProvider);

        publishedObject = new PublishedObject(keyPair, keyPair.getManifestFilename(), manifestCms.getEncoded(),
                false, keyPair.getCertificateRepositoryLocation(), manifestCms.getValidityPeriod(),
                manifestCms.getSigningTime());

        this.nextNumber++;
        this.needsReissuance = false;
    }

    private ManifestCms buildManifestCms(Map<String, Sha256> manifestEntries, KeyPair eeKeyPair, String signatureProvider) {
        ManifestCmsBuilder builder = new ManifestCmsBuilder();
        manifestEntries.forEach((fileName, sha256) -> builder.addFileHash(fileName, sha256.bytes()));
        builder.withCertificate(certificate.getCertificate());
        builder.withManifestNumber(BigInteger.valueOf(nextNumber));
        builder.withValidityPeriod(certificate.getValidityPeriod());
        builder.withSignatureProvider(signatureProvider);
        return builder.build(eeKeyPair.getPrivate());
    }

    public CertificateIssuanceRequest requestForManifestEeCertificate(KeyPair eeKeyPair) {
        IncomingResourceCertificate caCert = keyPair.getCurrentIncomingCertificate();
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
        X500Principal subject = ias.eeCertificateSubject(eeKeyPair.getPublic());
        X509CertificateInformationAccessDescriptor[] sia = ias.siaForSignedObjectCertificate(keyPair, "mft", subject, caCert.getSubjectPublicKey());
        return new CertificateIssuanceRequest(ResourceExtension.allInherited(), subject, eeKeyPair.getPublic(), sia);
    }

    private boolean isCloseToNextUpdateTime(DateTime now, ManifestCms cms) {
        return cms.getNextUpdateTime().minus(TIME_TO_NEXT_UPDATE_SOFT_LIMIT).isBefore(now);
    }

    private boolean parentCertificatePublicationLocationChanged(ManifestCms cms, IncomingResourceCertificate incomingResourceCertificate) {
        return !incomingResourceCertificate.getPublicationUri().equals(cms.getParentCertificateUri());
    }

    private void withdraw() {
        if (publishedObject != null) {
            publishedObject.withdraw();
        }
    }
}
