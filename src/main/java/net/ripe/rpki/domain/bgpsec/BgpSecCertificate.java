package net.ripe.rpki.domain.bgpsec;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateParser;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.domain.EmbeddedValidityPeriod;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.PublicKey;

@Entity
@Table(name = "bgpsec_certificate")
@SequenceGenerator(name = "seq_bgpseccertificate", sequenceName = "seq_all", allocationSize = 1)
@Getter
public class BgpSecCertificate extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bgpseccertificate")
    private Long id;

    @NotNull
    @Column(name = "serial_number", nullable = false)
    private BigInteger serial;

    @NotNull
    @Column(nullable = false)
    private X500Principal subject;

    @NotNull
    @Column(name = "subject_public_key", nullable = false)
    private byte[] encodedSubjectPublicKey;

    @Transient
    private PublicKey subjectPublicKey;

    @NotNull
    @Column(nullable = false)
    private X500Principal issuer;

    @NotNull
    @Embedded
    @AttributeOverride(name = "notValidBefore", column = @Column(name = "validity_not_before", nullable = false))
    @AttributeOverride(name = "notValidAfter", column = @Column(name = "validity_not_after", nullable = false))
    private EmbeddedValidityPeriod validityPeriod;

    @NotNull
    @Column(nullable = false)
    private byte[] encoded;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "signing_keypair_id")
    private KeyPairEntity signingKeyPair;

    @NotNull
    @Column(name = "asns", nullable = false)
    private String asns;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Setter
    private CertificateStatus status = CertificateStatus.CURRENT;

    @Column(name = "revocationtime")
    @Setter
    private DateTime revocationTime;

    protected BgpSecCertificate() {
    }

    public BgpSecCertificate(X509RouterCertificate certificate, KeyPairEntity signingKeyPair, Asn asn) {
        Validate.notNull(certificate, "certificate is required");
        Validate.notNull(signingKeyPair, "signingKeyPair is required");
        Validate.notNull(asn, "asn is required");

        updateCertificate(certificate);
        this.signingKeyPair = signingKeyPair;
        this.asns = asn.toString();
        revalidateCertificate();
    }

    private void updateCertificate(X509RouterCertificate certificate) {
        this.serial = certificate.getCertificate().getSerialNumber();
        this.subject = certificate.getSubject();
        this.issuer = certificate.getIssuer();
        this.subjectPublicKey = certificate.getPublicKey();
        this.encodedSubjectPublicKey = certificate.getPublicKey().getEncoded();
        this.validityPeriod = new EmbeddedValidityPeriod(certificate.getValidityPeriod());
        this.encoded = certificate.getEncoded();
        revalidateCertificate();
    }

    private void revalidateCertificate() {
        Validate.notNull(serial);
        Validate.notNull(subject);
        Validate.notNull(issuer);
        Validate.notNull(subjectPublicKey);
        Validate.notNull(encodedSubjectPublicKey);
        Validate.notNull(validityPeriod);
        Validate.notNull(encoded);
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

    public X509RouterCertificate getCertificate() {
        X509CertificateParser<X509RouterCertificate> parser = new X509RouterCertificateParser();
        parser.parse(ValidationResult.withLocation("bgpsec-cert-id_" + id), encoded);
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

    public boolean isValid() {
        DateTime now = DateTime.now();
        return !now.isBefore(getNotValidBefore()) && !now.isAfter(getNotValidAfter());
    }

    public void revoke() {
        if (isValid()) {
            status = CertificateStatus.REVOKED;
            revocationTime = new DateTime(DateTimeZone.UTC);
        }
    }
}
