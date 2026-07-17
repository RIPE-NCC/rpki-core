package net.ripe.rpki.domain.bgpsec;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificateBuilder;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.ripe.rpki.domain.TestObjects.*;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BgpSecEntityServiceBeanTest {
    private static final DateTime NOW = new DateTime();

    private final BgpSecConfigurationRepository bgpSecConfigurationRepository = mock(BgpSecConfigurationRepository.class);
    private final BgpSecEntityRepository bgpSecEntityRepository = mock(BgpSecEntityRepository.class);
    private final BgpSecCertificateRepository bgpSecCertificateRepository = mock(BgpSecCertificateRepository.class);
    private final ResourceCertificateRepository resourceCertificateRepository = mock(ResourceCertificateRepository.class);
    private final ProductionCertificateAuthority productionCA = new ProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, UUID.randomUUID(), null);
    private final CertificationProviderConfigurationData providerConfigurationData = new CertificationProviderConfigurationData(
            "SUN", "SunRsaSign", "SunRsaSign", "JKS"
    );
    private final CertificateFactory certificateFactory = new CertificateFactory(resourceCertificateRepository, bgpSecCertificateRepository, providerConfigurationData);
    private final X500Principal caSubject = new X500Principal("CN=MY-CA");

    private final Asn asn = new Asn(42);
    private final ValidityPeriod validity = new ValidityPeriod(NOW, NOW.plusYears(1));

    private final HostedCertificateAuthority ca = setupHostedCa(caSubject, productionCA, validity, asn);

    private final BgpSecEntityServiceBean subject = new BgpSecEntityServiceBean(
            null,
            bgpSecConfigurationRepository,
            bgpSecEntityRepository,
            certificateFactory
    );

    @Test
    void updateBgpSecIfNeeded_creates_an_entity_for_new_configuration() {
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(
                new BgpSecConfiguration(ca, asn, 0L, CSR)
        ));
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of());

        subject.updateBgpSecIfNeeded(ca);

        var bgpsecEntity = ArgumentCaptor.forClass(BgpSecEntity.class);
        verify(bgpSecEntityRepository).add(bgpsecEntity.capture());

        var entity = bgpsecEntity.getValue();
        assertThat(entity).isNotNull();
        var cert = entity.getCertificate();
        assertThat(cert).isNotNull();
        assertThat(cert.getAsns()).isEqualTo(asn.getStart().toString());
        assertThat(cert.getNotValidAfter()).isEqualTo(ca.getCurrentIncomingCertificate().getNotValidAfter());
        assertThat(cert.getNotValidBefore()).isLessThanOrEqualTo(new DateTime());
    }

    @Test
    void updateBgpSecIfNeeded_revokes_an_entity_with_no_configuration() {
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of());
        var cert = createBgpSecCertificate(
            asn,
            Csr.getPublicKey(CSR),
            ca.getCurrentIncomingCertificate().getNotValidBefore(),
            ca.getCurrentIncomingCertificate().getNotValidAfter(),
            CertificateStatus.CURRENT
        );
        BgpSecEntity entity = createEntityFromCertificate(asn, 3L, cert);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(entity));
        subject.updateBgpSecIfNeeded(ca);
        verify(bgpSecEntityRepository).remove(entity);
        verify(bgpSecCertificateRepository, never()).add(any(BgpSecCertificate.class));
    }

    @Test
    void reissues_entity_whose_parent_certificate_is_reissued() {
        BgpSecConfiguration config = new BgpSecConfiguration(ca, asn, 0L, CSR);
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of(config));

        var certificate = createBgpSecCertificate(
            config.getAsn(),
            Csr.getPublicKey(CSR),
            ca.getCurrentIncomingCertificate().getNotValidBefore(),
            ca.getCurrentIncomingCertificate().getNotValidAfter().minusDays(1),
            CertificateStatus.CURRENT
        );
        BgpSecEntity expiresBeforeParent = createEntityFromCertificate(config.getAsn(), config.getRouterId(), certificate);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(expiresBeforeParent));

        subject.updateBgpSecIfNeeded(ca);
        verify(bgpSecEntityRepository).remove(expiresBeforeParent);
        var reissuedEntity = ArgumentCaptor.forClass(BgpSecEntity.class);
        verify(bgpSecEntityRepository).add(reissuedEntity.capture());

        var entity = reissuedEntity.getValue();
        assertThat(entity).isNotNull();
        var cert = entity.getCertificate();
        assertThat(cert).isNotNull();
        assertThat(cert.getAsns()).isEqualTo(asn.getStart().toString());
        assertThat(cert.getNotValidAfter()).isEqualTo(ca.getCurrentIncomingCertificate().getNotValidAfter());
    }

    @Test
    void revoke_entity_when_bgpsec_certificate_is_expired() {
        when(bgpSecConfigurationRepository.findByCertificateAuthority(ca)).thenReturn(List.of());

        var certificate = createBgpSecCertificate(
            asn,
            Csr.getPublicKey(CSR),
            ca.getCurrentIncomingCertificate().getNotValidBefore(),
            ca.getCurrentIncomingCertificate().getNotValidAfter(),
            CertificateStatus.EXPIRED
        );
        BgpSecEntity expiredCertificate = createEntityFromCertificate(Asn.parse("AS64498"), 0L, certificate);
        when(bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)).thenReturn(List.of(expiredCertificate));

        subject.updateBgpSecIfNeeded(ca);
        verify(bgpSecEntityRepository).remove(expiredCertificate);
        verify(bgpSecCertificateRepository, never()).add(any(BgpSecCertificate.class));
    }

    private HostedCertificateAuthority setupHostedCa(X500Principal subject, ProductionCertificateAuthority productionCA, ValidityPeriod validity, Asn asn) {
        var ca = new HostedCertificateAuthority(
                42L,
                subject,
                UUID.randomUUID(),
                productionCA
        );
        var keypair = TestObjects.createTestKeyPair();
        var resourceCertificate = TestObjects.createResourceCertificate(12L, keypair, validity, TEST_RESOURCE_SET.add(asn), SUBJECT_INFORMATION_ACCESS);
        keypair.updateIncomingResourceCertificate(new CertificateIssuanceResponse(resourceCertificate.getCertificate(), resourceCertificate.getPublicationUri()));
        ca.addKeyPair(keypair);
        ca.activatePendingKeys(Duration.ZERO);
        return ca;
    }

    private BgpSecEntity createEntityFromCertificate(Asn asn, long routerId, BgpSecCertificate bgpSecCertificate) {
        var ski = X509CertificateUtil.getSubjectKeyIdentifier(bgpSecCertificate.getCertificate().getCertificate());
        return new BgpSecEntity(
            asn,
            HexFormat.of().formatHex(ski).toUpperCase(Locale.ROOT),
            routerId,
            bgpSecCertificate,
            "bgpsec-3.cer",
            URI.create("rsync://localhost/bgpsec/")
        );
    }

    private BgpSecCertificate createBgpSecCertificate(
        Asn asn,
        PublicKey publicKey,
        DateTime notBefore,
        DateTime notAfter,
        CertificateStatus status
    ) {
        var signingKeyPair = ca.getCurrentKeyPair();
        var currentIncomingCert = signingKeyPair.getCurrentIncomingCertificate();

        var builder = new X509RouterCertificateBuilder();
        builder.withKeyUsage(KeyUsage.digitalSignature);
        builder.withSignatureProvider(providerConfigurationData.getSignatureProvider());
        builder.withAsns(new int[] { asn.getValue().intValueExact() });
        builder.withSerial(java.math.BigInteger.valueOf(1L))
                .withSubjectDN(caSubject)
                .withPublicKey(publicKey)
                .withIssuerDN(currentIncomingCert.getSubject())
                .withValidityPeriod(new ValidityPeriod(notBefore, notAfter))
                .withSigningKeyPair(signingKeyPair.getKeyPair())
                .withAuthorityInformationAccess(new ResourceCertificateInformationAccessStrategyBean().aiaForCertificate(currentIncomingCert))
                .withCrlDistributionPoints(signingKeyPair.crlLocationUri());

        X509RouterCertificate x509Cert = builder.build();
        BgpSecCertificate cert = new BgpSecCertificate(x509Cert, signingKeyPair, asn);
        cert.setStatus(status);
        return cert;
    }
}
