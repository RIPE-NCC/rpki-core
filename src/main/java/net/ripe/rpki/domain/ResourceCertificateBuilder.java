package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import org.apache.commons.lang.Validate;
import org.bouncycastle.asn1.x509.KeyUsage;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.EnumSet;

public class ResourceCertificateBuilder {

    private BigInteger serial;
    private EnumSet<IpResourceType> inheritedResourceTypes = EnumSet.noneOf(IpResourceType.class);
    private ImmutableResourceSet resources = ImmutableResourceSet.empty();
    private X500Principal subjectDN;
    private PublicKey subjectPublicKey;
    private X500Principal issuerDN;
    private ValidityPeriod validityPeriod;
    private KeyPairEntity signingKeyPair;
    private boolean ca;
    private boolean embedded;
    private X509CertificateInformationAccessDescriptor[] authorityInformationAccess;
    private X509CertificateInformationAccessDescriptor[] subjectInformationAccess;
    private URI[] crlDistributionPoints;
    private String filename;
    private URI directory;


    public ResourceCertificateBuilder withSerial(BigInteger serial) {
        this.serial = serial;
        return this;
    }

    public ResourceCertificateBuilder withInheritedResourceTypes(EnumSet<IpResourceType> resourceTypes) {
        this.inheritedResourceTypes = EnumSet.copyOf(resourceTypes);
        return this;
    }

    public ResourceCertificateBuilder withResources(ImmutableResourceSet resources) {
        this.resources = resources;
        return this;
    }

    public ResourceCertificateBuilder withSubjectDN(X500Principal subjectDN) {
        this.subjectDN = subjectDN;
        return this;
    }

    public X500Principal getSubjectDN() {
        return subjectDN;
    }

    public ResourceCertificateBuilder withSubjectPublicKey(PublicKey subjectPublicKey) {
        this.subjectPublicKey = subjectPublicKey;
        return this;
    }

    public PublicKey getSubjectPublicKey() {
        return subjectPublicKey;
    }

    public ResourceCertificateBuilder withIssuerDN(X500Principal issuerDN) {
        this.issuerDN = issuerDN;
        return this;
    }

    public X500Principal getIssuerDN() {
        return issuerDN;
    }

    public ResourceCertificateBuilder withValidityPeriod(ValidityPeriod validityPeriod) {
        Validate.isTrue(validityPeriod.isClosed(), "validity period must be fully specified");
        this.validityPeriod = validityPeriod;
        return this;
    }

    public ResourceCertificateBuilder withSigningKeyPair(KeyPairEntity signingKeyPair) {
        this.signingKeyPair = signingKeyPair;
        return this;
    }

    public KeyPairEntity getSigningKeyPair() {
        return signingKeyPair;
    }

    public ResourceCertificateBuilder withCa(boolean ca) {
        this.ca = ca;
        return this;
    }

    public ResourceCertificateBuilder withEmbedded(boolean embedded) {
        this.embedded = embedded;
        return this;
    }

    public ResourceCertificateBuilder withAuthorityInformationAccess(X509CertificateInformationAccessDescriptor... descriptors) {
        this.authorityInformationAccess = descriptors;
        return this;
    }

    public ResourceCertificateBuilder withoutAuthorityInformationAccess() {
        authorityInformationAccess = null;
        return this;
    }

    public ResourceCertificateBuilder withSubjectInformationAccess(X509CertificateInformationAccessDescriptor... descriptors) {
        this.subjectInformationAccess = descriptors;
        return this;
    }

    public ResourceCertificateBuilder withCrlDistributionPoints(URI... crlDistributionPoints) {
        // ArrayIsStoredDirectly
        this.crlDistributionPoints = crlDistributionPoints;
        return this;
    }

    public ResourceCertificateBuilder withParentPublicationDirectory(URI directory) {
        this.directory = directory;
        return this;
    }

    public OutgoingResourceCertificate build() {
        X509ResourceCertificateBuilder builder = new X509ResourceCertificateBuilder();
        builder.withIssuerDN(issuerDN);
        builder.withSubjectDN(subjectDN);
        builder.withPublicKey(subjectPublicKey);
        builder.withSigningKeyPair(signingKeyPair.getKeyPair());
        builder.withSignatureProvider(signingKeyPair.getSignatureProvider());
        builder.withValidityPeriod(validityPeriod);
        builder.withCa(ca);
        if (ca) {
            builder.withKeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign);
        } else {
            builder.withKeyUsage(KeyUsage.digitalSignature);
        }
        if (authorityInformationAccess != null) {
            builder.withAuthorityInformationAccess(authorityInformationAccess);
        }
        if (subjectInformationAccess != null) {
            builder.withSubjectInformationAccess(subjectInformationAccess);
        }
        if (crlDistributionPoints != null) {
            builder.withCrlDistributionPoints(crlDistributionPoints);
        }
        if (Arrays.equals(subjectPublicKey.getEncoded(), signingKeyPair.getPublicKey().getEncoded())) {
            // Self-signed certificate MUST NOT have the authority key identifier extension.
            builder.withAuthorityKeyIdentifier(false);
        }
        builder.withSerial(serial);
        builder.withResources(new IpResourceSet(resources)).withInheritedResourceTypes(inheritedResourceTypes);
        X509ResourceCertificate cert = builder.build();
        return new OutgoingResourceCertificate(cert, signingKeyPair, embedded, filename, directory);
    }

    public ResourceCertificateBuilder withFilename(String filename) {
        this.filename = filename;
        return this;
    }
}
