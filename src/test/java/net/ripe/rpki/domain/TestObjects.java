package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestBuilder;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.hsm.Keys;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang.Validate;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.auth.x500.X500Principal;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static net.ripe.rpki.commons.crypto.util.KeyStoreUtilTest.DEFAULT_KEYSTORE_PROVIDER;
import static net.ripe.rpki.commons.crypto.util.KeyStoreUtilTest.DEFAULT_KEYSTORE_TYPE;
import static net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Constants and factory methods to create test objects suitable for pure unit testing. If you need to have a running
 * Spring application extends {@link CertificationDomainTestCase} instead and use ots non-static creation methods.
 */
public class TestObjects {

    static {
        // When running unit tests independently the `Keys` instance may not have been initialized yet, do so here
        // before creating any test key pairs.
        Keys.initialize(Optional.empty());
    }

    public static final long CA_ID = 42L;
    public static final long ACA_ID = 45L;
    public static final X500Principal PRODUCTION_CA_NAME = new X500Principal("O=ORG-TEST-PRODUCTION-CA");
    public static final X500Principal ALL_RESOURCES_CA_NAME = new X500Principal("CN=Test All Resources CA");
    public static final ImmutableResourceSet DEFAULT_PRODUCTION_CA_RESOURCES = ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
    public static final ImmutableResourceSet PRODUCTION_CA_RESOURCES = ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
    public static final URI BASE_URI = URI.create("rsync://localhost:20873/repository/");

    public static final URI CERTIFICATE_REPOSITORY_LOCATION = URI.create("rsync://localhost/bar/");
    public static final String PUBLICATION_FILENAME = "publication-uri.cer";
    public static final URI PUBLICATION_URI = CERTIFICATE_REPOSITORY_LOCATION.resolve(PUBLICATION_FILENAME);
    public static final Long TEST_SERIAL_NUMBER = 900L;
    public static final X509CertificateInformationAccessDescriptor[] AUTHORITY_INFORMATION_ACCESS = new X509CertificateInformationAccessDescriptor[] {
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_CA_CA_ISSUERS, URI.create("rsync://localhost/foo/aia-uri.cer"))
    };
    public static final X509CertificateInformationAccessDescriptor[] SUBJECT_INFORMATION_ACCESS = new X509CertificateInformationAccessDescriptor[] {
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, URI.create("rsync://localhost/foo/ca-repository-uri/")),
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create("rsync://localhost/foo/ca-repository-uri/manifest-uri.mft"))
    };
    public static final ImmutableResourceSet TEST_RESOURCE_SET = ImmutableResourceSet.parse("10.0.0.0/16, AS21212");
    public static final ValidityPeriod TEST_VALIDITY_PERIOD = new ValidityPeriod(new DateTime(2008, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC), new DateTime(2009, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC));
    public static final X500Principal TEST_SELF_SIGNED_CERTIFICATE_NAME = new X500Principal("CN=For Testing Only, C=NL");
    public static final KeyPairEntity TEST_KEY_PAIR_2 = createTestKeyPair(KeyPairEntityTest.TEST_KEY_PAIR_NAME + "-2");
    public static final X509CertificateInformationAccessDescriptor[] EE_CERT_SIA = {
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, URI.create("rsync://example.com/rpki-rsync/signed-object.roa"))
    };

    private static final AtomicLong serial = new AtomicLong(0L);

    public static KeyPairEntity createTestKeyPair(String name) {
        KeyPairEntitySignInfo signInfo = new KeyPairEntitySignInfo(DEFAULT_KEYSTORE_PROVIDER,
                DEFAULT_SIGNATURE_PROVIDER,
                DEFAULT_KEYSTORE_TYPE);
        try {
            return new KeyPairEntity(KeyPairFactoryTest.getKeyPair(name), signInfo,
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
                                                                        ImmutableResourceSet resources,
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
                                                                                ImmutableResourceSet resources,
                                                                                X509CertificateInformationAccessDescriptor[] subjectInformationAccess) {
        ResourceCertificateBuilder builder = createBuilder(signingKeyPair, subjectPublicKey);
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

    public static ResourceCertificateBuilder createBuilder(KeyPairEntity signingKeyPair, PublicKey subjectPublicKey) {
        ResourceCertificateBuilder builder = new ResourceCertificateBuilder();
        builder.withSerial(BigInteger.valueOf(TEST_SERIAL_NUMBER)).withCa(false).withEmbedded(true);
        builder.withSubjectDN(TEST_SELF_SIGNED_CERTIFICATE_NAME);
        builder.withIssuerDN(TEST_SELF_SIGNED_CERTIFICATE_NAME);
        builder.withSubjectPublicKey(subjectPublicKey);
        builder.withSigningKeyPair(signingKeyPair);
        builder.withAuthorityInformationAccess(AUTHORITY_INFORMATION_ACCESS);
        builder.withSubjectInformationAccess(SUBJECT_INFORMATION_ACCESS);
        builder.withParentPublicationDirectory(CERTIFICATE_REPOSITORY_LOCATION);
        DateTime now = new DateTime();
        builder.withValidityPeriod(new ValidityPeriod(now, now.plusYears(1)));
        return builder;
    }

    public static ProductionCertificateAuthority createInitialisedProdCaWithRipeResources() {
        RepositoryConfiguration certificationConfiguration = mock(RepositoryConfiguration.class);
        when(certificationConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        return createInitialisedProdCaWithRipeResources(new InMemoryResourceCertificateRepository(), certificationConfiguration);
    }

    public static ProductionCertificateAuthority createInitialisedProdCaWithRipeResources(ResourceCertificateRepository resourceCertificateRepository, RepositoryConfiguration certificationConfiguration) {
        CertificateRequestCreationService certificateRequestCreationService = new CertificateRequestCreationServiceBean(certificationConfiguration, null);
        ProductionCertificateAuthority ca = new ProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, UUID.randomUUID(), null);
        KeyPairEntity kp = createActiveKeyPair("TEST-KEY");

        ca.addKeyPair(kp);

        issueSelfSignedCertificate(resourceCertificateRepository, certificationConfiguration, certificateRequestCreationService, ca, kp);

        Validate.isTrue(kp.isCurrent());

        return ca;
    }

    static void issueSelfSignedCertificate(ResourceCertificateRepository resourceCertificateRepository, RepositoryConfiguration certificationConfiguration, CertificateRequestCreationService certificateRequestCreationService, ProductionCertificateAuthority ca, KeyPairEntity kp) {
        List<SigningRequest> signingRequests = certificateRequestCreationService.requestProductionCertificates(PRODUCTION_CA_RESOURCES, ca);
        SigningRequest request = signingRequests.get(0);
        CertificateIssuanceResponse response = makeSelfSignedCertificate(resourceCertificateRepository, certificationConfiguration, kp,
            request.getResourceCertificateRequest().getSubjectDN(), ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES);
        ca.updateIncomingResourceCertificate(kp, response.getCertificate(), response.getPublicationUri());
    }

    static CertificateIssuanceResponse makeSelfSignedCertificate(ResourceCertificateRepository resourceCertificateRepository,
                                                                 RepositoryConfiguration configuration,
                                                                 KeyPairEntity signingKeyPair,
                                                                 X500Principal subject,
                                                                 ImmutableResourceSet resources) {
        DateTime now = new DateTime(DateTimeZone.UTC);
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
        X509CertificateInformationAccessDescriptor[] sia = {
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                        configuration.getPublicRepositoryUri().resolve(DEFAULT_RESOURCE_CLASS)),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                        configuration.getPublicRepositoryUri().resolve(DEFAULT_RESOURCE_CLASS).resolve(signingKeyPair.getManifestFilename())),
        };
        ResourceCertificateBuilder builder = new ResourceCertificateBuilder();
        builder.withCa(true).withEmbedded(false);
        builder.withSubjectDN(subject).withIssuerDN(subject);
        builder.withSubjectPublicKey(signingKeyPair.getPublicKey());
        builder.withSigningKeyPair(signingKeyPair);
        builder.withResources(resources);
        builder.withValidityPeriod(new ValidityPeriod(now, CertificateAuthority.calculateValidityNotAfter(now)));
        builder.withoutAuthorityInformationAccess();
        builder.withFilename(ias.caCertificateFilename(signingKeyPair.getPublicKey()));
        builder.withParentPublicationDirectory(BASE_URI);
        builder.withSubjectInformationAccess(sia);
        builder.withSerial(SerialNumberSupplier.getInstance().get());
        OutgoingResourceCertificate outgoing = builder.build();

        resourceCertificateRepository.add(outgoing);
        return new CertificateIssuanceResponse(outgoing.getCertificate(), outgoing.getPublicationUri());
    }
}
