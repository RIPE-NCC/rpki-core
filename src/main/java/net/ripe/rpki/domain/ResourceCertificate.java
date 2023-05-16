package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

import javax.persistence.*;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.util.EnumSet;

import static java.util.Objects.requireNonNull;

/**
 * Contains the meta information of an X509 certificate. It's used for managing
 * the lifecycle of a X509 resource certificate.
 */
@Entity
@Table(name = "resourcecertificate")
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE")
@SequenceGenerator(name = "seq_resourcecertificate", sequenceName = "seq_all", allocationSize=1)
public abstract class ResourceCertificate extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_resourcecertificate")
    private Long id;

    @NotNull
    @Column(name = "serial_number", nullable = false)
    @Getter
    private BigInteger serial;

    @Column(name = "asn_inherited", nullable = false)
    private boolean asnInherited;

    @Column(name = "ipv4_inherited", nullable = false)
    private boolean ipv4Inherited;

    @Column(name = "ipv6_inherited", nullable = false)
    private boolean ipv6Inherited;

    @NotNull
    @Column(nullable = false)
    @Getter
    private ImmutableResourceSet resources;

    @NotNull
    @Column(nullable = false)
    @Getter
    private X500Principal subject;

    @NotNull
    @Column(name = "subject_public_key", nullable = false)
    private byte[] encodedSubjectPublicKey;

    @Transient
    private PublicKey subjectPublicKey;

    /**
     * Distinguished name (X.501 format). Example: RIPE NCC is C=nl,CN=RIPE NCC.
     */
    @NotNull
    @Column(nullable = false)
    @Getter
    private X500Principal issuer;

    @NotNull
    @Embedded
    @AttributeOverride(name = "notValidBefore", column = @Column(name = "validity_not_before", nullable = false))
    @AttributeOverride(name = "notValidAfter", column = @Column(name = "validity_not_after", nullable = false))
    private EmbeddedValidityPeriod validityPeriod;

    @NotNull
    @Column(nullable = false)
    private byte[] encoded;

    @Column(name = "publicationuri", nullable = true)
    @Getter
    private URI publicationUri;

    protected ResourceCertificate() {
    }

    /**
     * Use {@link ResourceCertificateBuilder} to create resource certificates.
     */
    ResourceCertificate(X509ResourceCertificate certificate) {
        Validate.notNull(certificate, "certificate is required");
        updateCertificate(certificate);
    }

    @PostLoad
    private void updateInheritedFlags() {
        // Previously manifest certificates used inherited resources and the `resources` field was empty
        if (resources.isEmpty()) {
            asnInherited = true;
            ipv4Inherited = true;
            ipv6Inherited = true;
        }
    }

    protected void updateCertificate(X509ResourceCertificate certificate) {
        this.serial = certificate.getCertificate().getSerialNumber();
        this.subject = certificate.getSubject();
        this.issuer = certificate.getIssuer();
        this.asnInherited = certificate.isResourceTypesInherited(EnumSet.of(IpResourceType.ASN));
        this.ipv4Inherited = certificate.isResourceTypesInherited(EnumSet.of(IpResourceType.IPv4));
        this.ipv6Inherited = certificate.isResourceTypesInherited(EnumSet.of(IpResourceType.IPv6));
        this.resources = certificate.resources();
        this.subjectPublicKey = certificate.getPublicKey();
        this.encodedSubjectPublicKey = certificate.getPublicKey().getEncoded();
        this.validityPeriod = new EmbeddedValidityPeriod(certificate.getValidityPeriod());
        this.encoded = certificate.getEncoded();
    }

    @Override
    public Long getId() {
        return id;
    }

    public @NonNull ResourceExtension getResourceExtension() {
        var inherited = EnumSet.noneOf(IpResourceType.class);
        if (asnInherited) inherited.add(IpResourceType.ASN);
        if (ipv4Inherited) inherited.add(IpResourceType.IPv4);
        if (ipv6Inherited) inherited.add(IpResourceType.IPv6);
        return ResourceExtension.of(inherited, resources);
    }

    public ValidityPeriod getValidityPeriod() {
        return validityPeriod.toValidityPeriod();
    }

    public DateTime getNotValidBefore() {
        return validityPeriod.getNotValidBefore();
    }

    public DateTime getNotValidAfter() {
        return validityPeriod.getNotValidAfter();
    }

    public PublicKey getSubjectPublicKey() {
        if (subjectPublicKey == null) {
            subjectPublicKey = KeyPairFactory.decodePublicKey(encodedSubjectPublicKey);
        }
        return subjectPublicKey;
    }

    public X509ResourceCertificate getCertificate() {
        X509ResourceCertificateParser parser = new X509ResourceCertificateParser();
        parser.parse(ValidationResult.withLocation("cert-id_" + id), encoded);
        return parser.getCertificate();
    }

    public byte[] getDerEncoded() {
        return encoded;
    }

    public X509CertificateInformationAccessDescriptor[] getAia() {
        return getCertificate().getAuthorityInformationAccess();
    }

    public X509CertificateInformationAccessDescriptor[] getSia() {
        return getCertificate().getSubjectInformationAccess();
    }

    public URI[] getCrlDistributionPoints() {
        return getCertificate().getCrlDistributionPoints();
    }

    public void setPublicationUri(URI publicationUri) {
        this.publicationUri = requireNonNull(publicationUri);
    }

    public ResourceCertificateData toData() {
        return new ResourceCertificateData(getCertificate(), getPublicationUri());
    }
}
