package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.hsm.Keys;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.auth.x500.X500Principal;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Optional;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicLong;

import static net.ripe.rpki.commons.crypto.util.KeyStoreUtilTest.DEFAULT_KEYSTORE_PROVIDER;
import static net.ripe.rpki.commons.crypto.util.KeyStoreUtilTest.DEFAULT_KEYSTORE_TYPE;
import static net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER;

public class TestObjects {

    static {
        // When running unit tests independently the `Keys` instance may not have been initialized yet, so do so here
        // before creating any test key pairs.
        Keys.initialize(Optional.empty());
    }

    public static final URI CERTIFICATE_REPOSITORY_LOCATION = URI.create("rsync://localhost/bar/");
    public static final URI PUBLICATION_URI = CERTIFICATE_REPOSITORY_LOCATION.resolve(OutgoingResourceCertificateTest.PUBLICATION_FILENAME);
    public static final X509CertificateInformationAccessDescriptor[] SUBJECT_INFORMATION_ACCESS = new X509CertificateInformationAccessDescriptor[] {
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, URI.create("rsync://localhost/foo/ca-repository-uri/")),
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create("rsync://localhost/foo/ca-repository-uri/manifest-uri.mft"))
    };
    public static final IpResourceSet TEST_RESOURCE_SET = IpResourceSet.parse("10.0.0.0/16, AS21212");
    public static final ValidityPeriod TEST_VALIDITY_PERIOD = new ValidityPeriod(new DateTime(2008, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC), new DateTime(2009, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC));
    public static final X500Principal TEST_SELF_SIGNED_CERTIFICATE_NAME = new X500Principal("CN=For Testing Only, C=NL");
    public static final KeyPairEntity TEST_KEY_PAIR_2 = createTestKeyPair(KeyPairEntityTest.TEST_KEY_PAIR_NAME + "-2");
    public static final X509CertificateInformationAccessDescriptor[] EE_CERT_SIA = {
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, URI.create("rsync://example.com/rpki-rsync/signed-object.roa"))
    };

    private static final AtomicLong serial = new AtomicLong(0L);

    public static KeyPairEntity createTestKeyPair(String name) {
        KeyPairEntityKeyInfo keyInfo = new KeyPairEntityKeyInfo(name, KeyPairFactoryTest.getKeyPair(name));
        KeyPairEntitySignInfo signInfo = new KeyPairEntitySignInfo(DEFAULT_KEYSTORE_PROVIDER,
                DEFAULT_SIGNATURE_PROVIDER,
                DEFAULT_KEYSTORE_TYPE);
        try {
            return new KeyPairEntity(keyInfo, signInfo,
                    URLEncoder.encode(name + ".crl", "utf-8"),
                    URLEncoder.encode(name + ".mft", "utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long nextSerial() {
        return serial.incrementAndGet();
    }

    public static IncomingResourceCertificate createResourceCertificate(Long serial, KeyPairEntity keyPair) {
        return createResourceCertificate(serial,
                keyPair,
                TEST_VALIDITY_PERIOD,
                TEST_RESOURCE_SET,
                SUBJECT_INFORMATION_ACCESS);
    }

    public static IncomingResourceCertificate createResourceCertificate(Long serial,
                                                                        KeyPairEntity keyPair,
                                                                        ValidityPeriod validityPeriod,
                                                                        IpResourceSet resources,
                                                                        X509CertificateInformationAccessDescriptor[] subjectInformationAccessDescriptors) {
        OutgoingResourceCertificate outgoing = createOutgoingResourceCertificate(serial,
                keyPair,
                keyPair.getPublicKey(),
                validityPeriod,
                resources,
                subjectInformationAccessDescriptors);
        return new IncomingResourceCertificate(outgoing.getCertificate(),
                PUBLICATION_URI,
                keyPair);
    }

    public static KeyPairEntity createTestKeyPair() {
        return createTestKeyPair(KeyPairEntityTest.TEST_KEY_PAIR_NAME);
    }

    public static KeyPairEntity createActiveKeyPair(String name) {
        KeyPairEntity testKeyPair = createTestKeyPair(name);
        IncomingResourceCertificate certificate = createResourceCertificate(nextSerial(), testKeyPair);
        testKeyPair.updateIncomingResourceCertificate(certificate.getCertificate(), certificate.getPublicationUri());
        testKeyPair.activate();
        return testKeyPair;
    }

    public static OutgoingResourceCertificate createOutgoingResourceCertificate(Long serial,
                                                                                KeyPairEntity signingKeyPair,
                                                                                PublicKey subjectPublicKey,
                                                                                ValidityPeriod validityPeriod,
                                                                                IpResourceSet resources,
                                                                                X509CertificateInformationAccessDescriptor[] subjectInformationAccess) {
        ResourceCertificateBuilder builder = OutgoingResourceCertificateTest.createBuilder(signingKeyPair, subjectPublicKey);
        builder.withSerial(BigInteger.valueOf(serial)).withCa(true).withEmbedded(false);
        builder.withSubjectDN(TEST_SELF_SIGNED_CERTIFICATE_NAME);
        builder.withIssuerDN(TEST_SELF_SIGNED_CERTIFICATE_NAME);
        builder.withValidityPeriod(validityPeriod).withResources(resources);
        builder.withFilename("test-certificate.cer");
        builder.withParentPublicationDirectory(CERTIFICATE_REPOSITORY_LOCATION);
        builder.withSubjectInformationAccess(subjectInformationAccess);
        builder.withResources(resources);
        return builder.build();
    }

    public static PKCS10CertificationRequest getPkcs10CertificationRequest(URI caRepositoryUri) {
        X500Principal subject = new X500Principal("CN=NON-HOSTED");
        RpkiCaCertificateRequestBuilder requestBuilder = new RpkiCaCertificateRequestBuilder().withSubject(subject)
                .withCaRepositoryUri(caRepositoryUri).withManifestUri(URI.create("rsync://tmp/manifest"));

        String name = "getPkcs10CertificationRequest:" + caRepositoryUri.toASCIIString().replaceAll("/", "-");
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        KeyPair keyPair = createTestKeyPair(name).getKeyPair();

        return requestBuilder.build(keyPair);
    }
}
