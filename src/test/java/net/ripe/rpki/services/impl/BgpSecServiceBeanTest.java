package net.ripe.rpki.services.impl;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.bgpsec.*;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest.createSelfSignedCaResourceCertificate;
import static net.ripe.rpki.domain.bgpsec.BgpSecCertificateFixtures.createBgpSecCertificateFromCSR;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BgpSecServiceBeanTest extends CertificationDomainTestCase {

    private static final long TEST_CA_ID = 123L;
    private static final URI BGPSEC_DIRECTORY = URI.create("rsync://localhost/bgpsec/");

    private CertificateAuthorityRepository caRepository;
    private BgpSecEntityRepository bgpSecEntityRepository;
    private TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private BgpSecCertificateRepository bgpSecCertificateRepositoryMock;
    private BgpSecServiceBean subjectWithMocks;
    private BgpSecConfigurationRepository bgpSecConfigurationRepository;
    ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);

    @Autowired
    private BgpSecConfigurationRepository realBgpSecConfigurationRepository;

    @Autowired
    private BgpSecEntityRepository realBgpSecEntityRepository;

    @Autowired
    private TrustAnchorPublishedObjectRepository realTrustAnchorPublishedObjectRepository;

    private BgpSecServiceBean realSubject;

    @Before
    public void setUp() {
        caRepository = mock(CertificateAuthorityRepository.class);
        bgpSecConfigurationRepository = mock(BgpSecConfigurationRepository.class);
        bgpSecEntityRepository = mock(BgpSecEntityRepository.class);
        trustAnchorPublishedObjectRepository = mock(TrustAnchorPublishedObjectRepository.class);
        bgpSecCertificateRepositoryMock = mock(BgpSecCertificateRepository.class);
        subjectWithMocks = new BgpSecServiceBean(caRepository, bgpSecConfigurationRepository,
                bgpSecEntityRepository, trustAnchorPublishedObjectRepository, bgpSecCertificateRepositoryMock);
        realSubject = new BgpSecServiceBean(certificateAuthorityRepository, realBgpSecConfigurationRepository,
                realBgpSecEntityRepository, realTrustAnchorPublishedObjectRepository, bgpSecCertificateRepository);
    }

    @Test
    public void shouldFingBgpSecConfiguration() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        var config = new BgpSecConfiguration(ca, Asn.parse("AS64496"), 0L, CSR);
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(config));
        assertThat(subjectWithMocks.findBgpSecConfiguration(TEST_CA_ID).getFirst()).isEqualTo(config.toData());
    }

    @Test
    public void shouldNotFindBgpSecConfigurationIfIdDoesNotMatch() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecCertificateRepositoryMock.findCurrentCertificateDataByCaId(TEST_CA_ID, 2L)).thenReturn(Optional.empty());

        assertThat(subjectWithMocks.findBgpSecCertificates(TEST_CA_ID, 2L)).isEmpty();
    }

    @Test
    @Transactional
    @Rollback
    public void shouldFindBgpSecConfigurationIfIdMatches() {
        clearDatabase();
        ManagedCertificateAuthority realCa = createInitialisedProdCaWithRipeResources();
        KeyPairEntity kp = realCa.getCurrentKeyPair();

        Asn asn = Asn.parse("AS64496");
        DateTime notBefore = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime notAfter = new DateTime(2025, 1, 1, 0, 0, 0, DateTimeZone.UTC);

        BgpSecConfiguration config = new BgpSecConfiguration(realCa, asn, 0L, CSR);
        realBgpSecConfigurationRepository.add(config);
        BgpSecCertificate cert = createBgpSecCertificateFromCSR(kp, CSR, 1L, notBefore, notAfter);
        bgpSecCertificateRepository.add(cert);
        realBgpSecEntityRepository.add(new BgpSecEntity(asn, Csr.getKeyIdentifier(CSR), 0L, cert, "bgpsec-1.cer", BGPSEC_DIRECTORY));
        entityManager.flush();

        assertThat(realSubject.findBgpSecCertificates(realCa.getId(), config.getId()))
                .isPresent()
                .hasValueSatisfying(result -> {
                    assertThat(result.id()).isEqualTo(config.getId());
                    assertThat(result.asn()).isEqualTo(asn);
                    assertThat(result.routerId()).isEqualTo(new RouterId(0L));
                    assertThat(result.csr()).isEqualTo(CSR);
                    assertThat(result.keyIdentifier()).isEqualTo(Csr.getKeyIdentifier(CSR));
                });
    }

    @Test
    public void shouldReturnEmptyPkcs7ChainWhenCaDoesNotExist() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(null);

        assertThat(subjectWithMocks.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB", null, null))).isEmpty();
    }

    @Test
    public void shouldReturnEmptyPkcs7WhenCaDoesNotExist() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(null);

        assertThat(subjectWithMocks.findBgpSecCertificatePkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB", null, null))).isEmpty();
    }

    @Test
    public void shouldReturnEmptyPkcs7ChainWhenNoMatchingBgpSecEntryExists() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of());

        assertThat(subjectWithMocks.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB", null, null))).isEmpty();
    }

    @Test
    public void shouldPackMatchingCertificateAsPkcs7() throws Exception {
        X509Certificate x509Certificate = readTestX509Certificate();

        ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);
        BgpSecEntity bgpSecEntity = mock(BgpSecEntity.class);
        BgpSecCertificate bgpSecCertificate = mock(BgpSecCertificate.class, RETURNS_DEEP_STUBS);
        KeyPairEntity signingKeyPair = mock(KeyPairEntity.class);
        IncomingResourceCertificate issuerCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate issuerResourceCertificate = mock(X509ResourceCertificate.class);

        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(bgpSecEntity));

        when(bgpSecEntity.getAsn()).thenReturn(Asn.parse("AS64496"));
        when(bgpSecEntity.getRouterId()).thenReturn(10L);
        when(bgpSecEntity.getKeyIdentifier()).thenReturn("AABB");
        when(bgpSecEntity.getCertificate()).thenReturn(bgpSecCertificate);

        when(bgpSecCertificate.getCertificate().getCertificate()).thenReturn(x509Certificate);
        when(bgpSecCertificate.getSigningKeyPair()).thenReturn(signingKeyPair);
        when(signingKeyPair.findCurrentIncomingCertificate()).thenReturn(java.util.Optional.of(issuerCertificate));
        when(issuerCertificate.getCertificate()).thenReturn(issuerResourceCertificate);
        when(issuerResourceCertificate.getCertificate()).thenReturn(x509Certificate);

        byte[] pkcs7 = subjectWithMocks.findBgpSecCertificatePkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb", null, null))
                .orElseThrow();

        assertThat(pkcs7).isNotNull();
        var certPath = CertificateFactory.getInstance("X.509").generateCertPath(new java.io.ByteArrayInputStream(pkcs7), "PKCS7");
        assertThat(certPath.getCertificates()).hasSize(1);

    }

    @Test
    public void shouldPackMatchingCertificateChainAsPkcs7() throws Exception {
        X509Certificate x509Certificate = readTestX509Certificate();

        ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);
        BgpSecEntity bgpSecEntity = mock(BgpSecEntity.class);
        BgpSecCertificate bgpSecCertificate = mock(BgpSecCertificate.class, RETURNS_DEEP_STUBS);
        KeyPairEntity signingKeyPair = mock(KeyPairEntity.class);
        IncomingResourceCertificate issuerCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate issuerResourceCertificate = mock(X509ResourceCertificate.class);

        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(bgpSecEntity));

        when(bgpSecEntity.getAsn()).thenReturn(Asn.parse("AS64496"));
        when(bgpSecEntity.getRouterId()).thenReturn(10L);
        when(bgpSecEntity.getKeyIdentifier()).thenReturn("AABB");
        when(bgpSecEntity.getCertificate()).thenReturn(bgpSecCertificate);

        when(bgpSecCertificate.getCertificate().getCertificate()).thenReturn(x509Certificate);
        when(bgpSecCertificate.getSigningKeyPair()).thenReturn(signingKeyPair);
        when(signingKeyPair.findCurrentIncomingCertificate()).thenReturn(java.util.Optional.of(issuerCertificate));
        when(issuerCertificate.getCertificate()).thenReturn(issuerResourceCertificate);
        when(issuerResourceCertificate.getCertificate()).thenReturn(x509Certificate);

        byte[] pkcs7 = subjectWithMocks.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb", null, null))
                .orElseThrow();

        var certPath = CertificateFactory.getInstance("X.509").generateCertPath(new java.io.ByteArrayInputStream(pkcs7), "PKCS7");

        assertThat(certPath.getCertificates()).hasSize(2);
    }

    @Test
    public void shouldPackCertificateChainRecursivelyToTrustAnchor() throws Exception {
        X509Certificate x509Certificate = readTestX509Certificate();

        ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);
        ManagedCertificateAuthority parentCa = mock(ManagedCertificateAuthority.class);
        BgpSecEntity bgpSecEntity = mock(BgpSecEntity.class);
        BgpSecCertificate bgpSecCertificate = mock(BgpSecCertificate.class, RETURNS_DEEP_STUBS);
        KeyPairEntity signingKeyPair = mock(KeyPairEntity.class);
        IncomingResourceCertificate issuerCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate issuerResourceCertificate = mock(X509ResourceCertificate.class);
        IncomingResourceCertificate parentIncomingCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate parentResourceCertificate = mock(X509ResourceCertificate.class);

        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(bgpSecEntity));

        when(ca.getParent()).thenReturn(parentCa);
        when(parentCa.findCurrentIncomingResourceCertificate()).thenReturn(java.util.Optional.of(parentIncomingCertificate));
        when(parentCa.getParent()).thenReturn(null);
        when(trustAnchorPublishedObjectRepository.findActiveObjects()).thenReturn(List.of());

        when(bgpSecEntity.getAsn()).thenReturn(Asn.parse("AS64496"));
        when(bgpSecEntity.getRouterId()).thenReturn(10L);
        when(bgpSecEntity.getKeyIdentifier()).thenReturn("AABB");
        when(bgpSecEntity.getCertificate()).thenReturn(bgpSecCertificate);

        when(bgpSecCertificate.getCertificate().getCertificate()).thenReturn(x509Certificate);
        when(bgpSecCertificate.getSigningKeyPair()).thenReturn(signingKeyPair);
        when(signingKeyPair.findCurrentIncomingCertificate()).thenReturn(java.util.Optional.of(issuerCertificate));
        when(issuerCertificate.getCertificate()).thenReturn(issuerResourceCertificate);
        when(issuerResourceCertificate.getCertificate()).thenReturn(x509Certificate);
        when(parentIncomingCertificate.getCertificate()).thenReturn(parentResourceCertificate);
        when(parentResourceCertificate.getCertificate()).thenReturn(x509Certificate);

        byte[] pkcs7 = subjectWithMocks.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb", null, null))
                .orElseThrow();

        var certPath = CertificateFactory.getInstance("X.509").generateCertPath(new java.io.ByteArrayInputStream(pkcs7), "PKCS7");

        assertThat(certPath.getCertificates()).hasSize(3);
    }

    @Test
    public void shouldAppendTrustAnchorCertificateWhenAvailable() throws Exception {
        X509Certificate x509Certificate = readTestX509Certificate();
        X509ResourceCertificate trustAnchorCertificate = createSelfSignedCaResourceCertificate(new IpResourceSet(ALL_PRIVATE_USE_RESOURCES));

        ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);
        ManagedCertificateAuthority parentCa = mock(ManagedCertificateAuthority.class);
        BgpSecEntity bgpSecEntity = mock(BgpSecEntity.class);
        BgpSecCertificate bgpSecCertificate = mock(BgpSecCertificate.class, RETURNS_DEEP_STUBS);
        KeyPairEntity signingKeyPair = mock(KeyPairEntity.class);
        IncomingResourceCertificate issuerCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate issuerResourceCertificate = mock(X509ResourceCertificate.class);
        IncomingResourceCertificate parentIncomingCertificate = mock(IncomingResourceCertificate.class);
        X509ResourceCertificate parentResourceCertificate = mock(X509ResourceCertificate.class);
        TrustAnchorPublishedObject trustAnchorPublishedObject = new TrustAnchorPublishedObject(
                URI.create("rsync://example.net/ta.cer"),
                trustAnchorCertificate.getEncoded(),
                trustAnchorCertificate.getValidityPeriod().getNotValidBefore().toInstant());

        trustAnchorPublishedObject.published();

        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(bgpSecEntity));
        when(trustAnchorPublishedObjectRepository.findActiveObjects()).thenReturn(List.of(trustAnchorPublishedObject));

        when(ca.getParent()).thenReturn(parentCa);
        when(parentCa.findCurrentIncomingResourceCertificate()).thenReturn(java.util.Optional.of(parentIncomingCertificate));
        when(parentCa.getParent()).thenReturn(null);

        when(bgpSecEntity.getAsn()).thenReturn(Asn.parse("AS64496"));
        when(bgpSecEntity.getRouterId()).thenReturn(10L);
        when(bgpSecEntity.getKeyIdentifier()).thenReturn("AABB");
        when(bgpSecEntity.getCertificate()).thenReturn(bgpSecCertificate);

        when(bgpSecCertificate.getCertificate().getCertificate()).thenReturn(x509Certificate);
        when(bgpSecCertificate.getSigningKeyPair()).thenReturn(signingKeyPair);
        when(signingKeyPair.findCurrentIncomingCertificate()).thenReturn(java.util.Optional.of(issuerCertificate));
        when(issuerCertificate.getCertificate()).thenReturn(issuerResourceCertificate);
        when(issuerResourceCertificate.getCertificate()).thenReturn(x509Certificate);
        when(parentIncomingCertificate.getCertificate()).thenReturn(parentResourceCertificate);
        when(parentResourceCertificate.getCertificate()).thenReturn(x509Certificate);

        byte[] pkcs7 = subjectWithMocks.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb", null, null))
                .orElseThrow();

        var certPath = CertificateFactory.getInstance("X.509")
                .generateCertPath(new java.io.ByteArrayInputStream(pkcs7), "PKCS7");

        assertThat(certPath.getCertificates()).hasSize(4);
    }

    private byte[] readTestCertificate() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/cert/idcert-1.cer")) {
            assertThat(inputStream).isNotNull();
            return inputStream.readAllBytes();
        }
    }

    private X509Certificate readTestX509Certificate() throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(readTestCertificate()));
    }

    @Test
    public void shouldReturnEmptyWhenCaDoesNotExist() {
        assertThat(realSubject.findBgpSecCertificates(TEST_CA_ID, 999L)).isEmpty();
    }

    @Test
    @Transactional
    @Rollback
    public void shouldMapRowToBgpSecConfigurationData() {
        clearDatabase();
        ManagedCertificateAuthority realCa = createInitialisedProdCaWithRipeResources();
        KeyPairEntity kp = realCa.getCurrentKeyPair();

        Asn asn = Asn.parse("AS64496");
        Long routerId = 10L;
        DateTime notBefore = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime notAfter = new DateTime(2025, 1, 1, 0, 0, 0, DateTimeZone.UTC);

        realBgpSecConfigurationRepository.add(new BgpSecConfiguration(realCa, asn, routerId, CSR));
        BgpSecCertificate cert = BgpSecCertificateFixtures.createBgpSecCertificateFromCSR(kp, CSR, 1L, notBefore, notAfter);
        bgpSecCertificateRepository.add(cert);
        realBgpSecEntityRepository.add(new BgpSecEntity(asn, Csr.getKeyIdentifier(CSR), routerId, cert, "bgpsec-1.cer", BGPSEC_DIRECTORY));
        entityManager.flush();

        var config = realBgpSecConfigurationRepository.findByCertificateAuthority(realCa).get(0);
        var result = realSubject.findBgpSecCertificates(realCa.getId(), config.getId());

        assertThat(result).isPresent().hasValueSatisfying(r -> {
            assertThat(r.id()).isNotNull();
            assertThat(r.asn()).isEqualTo(Asn.parse("AS64496"));
            assertThat(r.routerId()).isEqualTo(new RouterId(10L));
            assertThat(r.csr()).isEqualTo(CSR);
            assertThat(r.keyIdentifier()).isEqualTo(Csr.getKeyIdentifier(CSR));
            assertThat(r.notValidBefore()).isEqualTo(notBefore);
            assertThat(r.notValidAfter()).isEqualTo(notAfter);
        });
    }

    @Test
    @Transactional
    @Rollback
    public void shouldMapNullRouterIdToBgpSecConfigurationData() {
        clearDatabase();
        ManagedCertificateAuthority realCa = createInitialisedProdCaWithRipeResources();
        KeyPairEntity kp = realCa.getCurrentKeyPair();

        Asn asn = Asn.parse("AS64496");
        DateTime notBefore = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime notAfter = new DateTime(2025, 1, 1, 0, 0, 0, DateTimeZone.UTC);

        realBgpSecConfigurationRepository.add(new BgpSecConfiguration(realCa, asn, null, CSR));
        BgpSecCertificate cert = BgpSecCertificateFixtures.createBgpSecCertificateFromCSR(kp, CSR, 1L, notBefore, notAfter);
        bgpSecCertificateRepository.add(cert);
        realBgpSecEntityRepository.add(new BgpSecEntity(asn, Csr.getKeyIdentifier(CSR), null, cert, "bgpsec-1.cer", BGPSEC_DIRECTORY));
        entityManager.flush();

        var config = realBgpSecConfigurationRepository.findByCertificateAuthority(realCa).get(0);
        assertThat(realSubject.findBgpSecCertificates(realCa.getId(), config.getId()))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.routerId()).isNull());
    }

    @Test
    @Transactional
    @Rollback
    public void shouldReturnAllCertificateRows() {
        clearDatabase();
        ManagedCertificateAuthority realCa = createInitialisedProdCaWithRipeResources();
        KeyPairEntity kp = realCa.getCurrentKeyPair();

        DateTime notBefore = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime notAfter = new DateTime(2025, 1, 1, 0, 0, 0, DateTimeZone.UTC);

        Asn asn1 = Asn.parse("AS64496");
        BgpSecConfiguration config1 = new BgpSecConfiguration(realCa, asn1, 10L, CSR);
        realBgpSecConfigurationRepository.add(config1);
        BgpSecCertificate cert1 = BgpSecCertificateFixtures.createBgpSecCertificateFromCSR(kp, CSR, 1L, notBefore, notAfter);
        bgpSecCertificateRepository.add(cert1);
        realBgpSecEntityRepository.add(new BgpSecEntity(asn1, Csr.getKeyIdentifier(CSR), 10L, cert1, "bgpsec-1.cer", BGPSEC_DIRECTORY));

        Asn asn2 = Asn.parse("AS64497");
        BgpSecConfiguration config2 = new BgpSecConfiguration(realCa, asn2, null, CSR2);
        realBgpSecConfigurationRepository.add(config2);
        BgpSecCertificate cert2 = BgpSecCertificateFixtures.createBgpSecCertificateFromCSR(kp, CSR, 2L, notBefore, notAfter);
        bgpSecCertificateRepository.add(cert2);
        realBgpSecEntityRepository.add(new BgpSecEntity(asn2, Csr.getKeyIdentifier(CSR2), null, cert2, "bgpsec-2.cer", BGPSEC_DIRECTORY));

        entityManager.flush();

        assertThat(realSubject.findBgpSecCertificates(realCa.getId(), config1.getId())).isPresent();
        assertThat(realSubject.findBgpSecCertificates(realCa.getId(), config2.getId())).isPresent();
    }
}
