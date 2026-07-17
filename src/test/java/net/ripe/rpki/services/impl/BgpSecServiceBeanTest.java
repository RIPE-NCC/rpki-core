package net.ripe.rpki.services.impl;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.bgpsec.*;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import org.junit.Before;
import org.junit.Test;

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
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BgpSecServiceBeanTest {

    private static final long TEST_CA_ID = 123L;

    private CertificateAuthorityRepository caRepository;
    private BgpSecEntityRepository bgpSecEntityRepository;
    private TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private BgpSecServiceBean subject;
    private BgpSecConfigurationRepository bgpSecConfigurationRepository;
    ManagedCertificateAuthority ca = mock(ManagedCertificateAuthority.class);

    @Before
    public void setUp() {
        caRepository = mock(CertificateAuthorityRepository.class);
        bgpSecConfigurationRepository = mock(BgpSecConfigurationRepository.class);
        bgpSecEntityRepository = mock(BgpSecEntityRepository.class);
        trustAnchorPublishedObjectRepository = mock(TrustAnchorPublishedObjectRepository.class);
        subject = new BgpSecServiceBean(caRepository, bgpSecConfigurationRepository,
                bgpSecEntityRepository, trustAnchorPublishedObjectRepository);
    }

    @Test
    public void shouldFingBgpSecConfiguration() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        var config = new BgpSecConfiguration(ca, Asn.parse("AS64496"), 0L, CSR);
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(config));
        assertThat(subject.findBgpSecConfiguration(TEST_CA_ID).getFirst()).isEqualTo(config.toData());
    }

    @Test
    public void shouldNotFindBgpSecConfigurationIfIdDoesNotMatch() throws Exception {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);

        var config = new BgpSecConfiguration(ca, Asn.parse("AS64496"), 0L, CSR);
        setIdOnBgpSecConfiguration(config, 1L);

        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(config));

        assertThat(subject.findBgpSecConfigurationById(TEST_CA_ID, 2L)).isEmpty();
    }

    @Test
    public void shouldFindBgpSecConfigurationIfIdMatches() throws Exception {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);

        var config = new BgpSecConfiguration(ca, Asn.parse("AS64496"), 0L, CSR);
        setIdOnBgpSecConfiguration(config, 1L);

        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(config));

        assertThat(subject.findBgpSecConfigurationById(TEST_CA_ID, 1L)).isEqualTo(Optional.of(config.toData()));
    }

    private static void setIdOnBgpSecConfiguration(BgpSecConfiguration config, long value) throws NoSuchFieldException, IllegalAccessException {
        var idField = BgpSecConfiguration.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(config, value);
    }

    @Test
    public void shouldReturnEmptyPkcs7ChainWhenCaDoesNotExist() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(null);

        assertThat(subject.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB"))).isEmpty();
    }

    @Test
    public void shouldReturnEmptyPkcs7WhenCaDoesNotExist() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(null);

        assertThat(subject.findBgpSecCertificatePkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB"))).isEmpty();
    }

    @Test
    public void shouldReturnEmptyPkcs7ChainWhenNoMatchingBgpSecEntryExists() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(ca);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of());

        assertThat(subject.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "AABB"))).isEmpty();
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

        byte[] pkcs7 = subject.findBgpSecCertificatePkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb"))
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

        byte[] pkcs7 = subject.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb"))
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

        byte[] pkcs7 = subject.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb"))
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

        byte[] pkcs7 = subject.findBgpSecCertificateChainPkcs7(TEST_CA_ID,
                        new BgpSecConfigurationData(1L, Asn.parse("AS64496"), new RouterId(10L), CSR, "aabb"))
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
}
