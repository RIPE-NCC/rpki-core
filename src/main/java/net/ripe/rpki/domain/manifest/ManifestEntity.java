package net.ripe.rpki.domain.manifest;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsBuilder;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.joda.time.DateTime;
import org.joda.time.Period;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


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
     * The minimum time the current manifest or CRL still needs to be valid before we update it anyway. This is to avoid
     * not being on time to replace these objects. When the time to next update is less than this hard limit, the system
     * must issue a new manifest/CRL pair as soon as possible.
     */
    public static final Period TIME_TO_NEXT_UPDATE_HARD_LIMIT = Period.hours(15);

    /**
     * Manifests and CRLs are replaced as soon as the time to next update is less than this soft limit, but replacement
     * is spread out over time to reduce the load on the system and avoid spikes where we would suddenly republish the
     * majority of CRLs and manifests every 8 hours.
     */
    public static final Period TIME_TO_NEXT_UPDATE_SOFT_LIMIT = Period.hours(16);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_manifestentity")
    private Long id;

    @Column(name = "nextnumber", nullable = false)
    private long nextNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "keypair_id", nullable = false)
    private KeyPairEntity keyPair;

    @ManyToOne(optional = false, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "certificate_id", nullable = false)
    private OutgoingResourceCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "published_object_id", nullable = false)
    private PublishedObject publishedObject;

    @OneToMany(mappedBy = "containingManifest")
    private Set<PublishedObject> entries = new HashSet<>();

    protected ManifestEntity() {}

    public ManifestEntity(KeyPairEntity keyPair) {
        this.keyPair = keyPair;
        this.nextNumber = 1L;
    }

    @Override
    public Long getId() {
        return id;
    }

    public OutgoingResourceCertificate getCertificate() {
        return certificate;
    }

    public KeyPairEntity getKeyPair() {
        return keyPair;
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

    public boolean isUpdateNeeded(DateTime now, Collection<PublishedObject> manifestEntries, IncomingResourceCertificate currentCertificate) {
        ManifestCms cms = getManifestCms();
        return cms == null
                || isCloseToNextUpdateTime(now, cms)
                || parentCertificatePublicationLocationChanged(cms, currentCertificate)
                || !cms.matchesFiles(manifestEntries.stream().collect(Collectors.toMap(PublishedObject::getFilename, PublishedObject::getContent, (a, b) -> b)));
    }

    public void update(OutgoingResourceCertificate eeCertificate,
                       KeyPair eeCertificateKeyPair,
                       String signatureProvider,
                       Collection<PublishedObject> updatedEntries) {
        withdraw();

        this.certificate = eeCertificate;

        Set<PublishedObject> addedEntries = new HashSet<>(updatedEntries);
        addedEntries.removeAll(entries);
        for (PublishedObject addedEntry : addedEntries) {
            addedEntry.setContainingManifest(this);
            entries.add(addedEntry);
        }
        Set<PublishedObject> removedEntries = new HashSet<>(entries);
        removedEntries.removeAll(updatedEntries);
        for (PublishedObject removedEntry : removedEntries) {
            removedEntry.setContainingManifest(null);
            entries.remove(removedEntry);
        }

        ManifestCms manifestCms = buildManifestCms(entries, eeCertificateKeyPair, signatureProvider);

        publishedObject = new PublishedObject(keyPair, keyPair.getManifestFilename(), manifestCms.getEncoded(), false, keyPair.getCertificateRepositoryLocation(), manifestCms.getValidityPeriod());

        this.nextNumber++;
    }

    private ManifestCms buildManifestCms(Collection<PublishedObject> manifestEntries, KeyPair eeKeyPair, String signatureProvider) {
        ManifestCmsBuilder builder = new ManifestCmsBuilder();
        for (PublishedObject manifestEntry : manifestEntries) {
            builder.addFile(manifestEntry.getFilename(), manifestEntry.getContent());
        }
        builder.withCertificate(certificate.getCertificate());
        builder.withManifestNumber(BigInteger.valueOf(nextNumber));
        builder.withThisUpdateTime(certificate.getNotValidBefore());
        builder.withNextUpdateTime(certificate.getNotValidAfter());
        builder.withSignatureProvider(signatureProvider);
        return builder.build(eeKeyPair.getPrivate());
    }

    public CertificateIssuanceRequest requestForManifestEeCertificate(KeyPair eeKeyPair) {
        IncomingResourceCertificate caCert = keyPair.getCurrentIncomingCertificate();
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
        X500Principal subject = ias.eeCertificateSubject("manifest", eeKeyPair.getPublic(), keyPair);
        X509CertificateInformationAccessDescriptor[] sia = ias.siaForSignedObjectCertificate(keyPair, "mft", subject, caCert.getSubjectPublicKey());
        return new CertificateIssuanceRequest(new IpResourceSet(), subject, eeKeyPair.getPublic(), sia);
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
