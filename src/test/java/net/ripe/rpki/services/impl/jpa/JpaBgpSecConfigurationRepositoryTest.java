package net.ripe.rpki.services.impl.jpa;

import jakarta.transaction.Transactional;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificate;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import net.ripe.rpki.domain.bgpsec.BgpSecEntity;
import net.ripe.rpki.domain.bgpsec.BgpSecEntityRepository;
import net.ripe.rpki.domain.bgpsec.Csr;
import net.ripe.rpki.domain.bgpsec.RouterId;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.domain.bgpsec.BgpSecCertificateFixtures.createBgpSecCertificateFromCSR;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Rollback
public class JpaBgpSecConfigurationRepositoryTest extends CertificationDomainTestCase {

    private static final Asn ASN = Asn.parse("AS64496");
    private static final Long ROUTER_ID = 123L;
    private static final URI DIRECTORY = URI.create("rsync://localhost/bgpsec/");

    @Autowired
    private JpaBgpSecConfigurationRepository subject;

    @Autowired
    private BgpSecEntityRepository bgpSecEntityRepository;

    private ProductionCertificateAuthority ca;

    @Before
    public void setUp() {
        clearDatabase();
        ca = TestObjects.createInitialisedProdCaWithRipeResources();
        entityManager.persist(ca);
    }

    @Test
    public void shouldReturnEmpty() {
        assertThat(subject.findByCertificateAuthority(ca)).isEmpty();
    }

    @Test
    public void shouldCreateAndGetBack() {
        BgpSecConfiguration bgpSec = new BgpSecConfiguration(ca, Asn.parse("AS1"), 3L, CSR);
        subject.add(bgpSec);
        List<BgpSecConfiguration> byCa = subject.findByCertificateAuthority(ca);
        assertThat(byCa).hasSize(1).allSatisfy(bgpSec1 -> assertThat(bgpSec).isEqualTo(bgpSec1));
    }

    @Test
    public void shouldGetBgpsecConfigurationById() {
        var bgpsec = new BgpSecConfiguration(ca, Asn.parse("AS1"), 3L, CSR);
        subject.add(bgpsec);
        var result = subject.findByCertificateAuthorityAndId(ca, bgpsec.getId());
        assertThat(result).isEqualTo(Optional.of(bgpsec));
    }

    @Test
    public void findCurrentConfigurationDataByCaId_unknownConfiguration_returnsEmpty() {
        assertThat(subject.findCurrentConfigurationDataByCaId(ca.getId(), -1L)).isEmpty();
    }

    @Test
    public void findCurrentConfigurationDataByCaId_withoutBgpSecEntity_returnsConfigurationWithoutValidity() {
        long configurationId = addConfiguration();

        assertThat(subject.findCurrentConfigurationDataByCaId(ca.getId(), configurationId)).hasValueSatisfying(data -> {
            assertThat(data.id()).isEqualTo(configurationId);
            assertThat(data.asn()).isEqualTo(ASN);
            assertThat(data.routerId()).isEqualTo(new RouterId(ROUTER_ID));
            assertThat(data.csr()).isEqualTo(CSR);
            assertThat(data.keyIdentifier()).isEqualTo(Csr.getKeyIdentifier(CSR));
            assertThat(data.notValidBefore()).isNull();
            assertThat(data.notValidAfter()).isNull();
        });
    }

    @Test
    public void findCurrentConfigurationDataByCaId_withoutCurrentCertificate_returnsConfigurationWithoutValidity() {
        long configurationId = addConfiguration();
        addBgpSecEntity(CertificateStatus.REVOKED);

        assertThat(subject.findCurrentConfigurationDataByCaId(ca.getId(), configurationId)).hasValueSatisfying(data -> {
            assertThat(data.id()).isEqualTo(configurationId);
            assertThat(data.notValidBefore()).isNull();
            assertThat(data.notValidAfter()).isNull();
        });
    }

    @Test
    public void findCurrentConfigurationDataByCaId_withCurrentCertificate_returnsConfigurationWithValidity() {
        long configurationId = addConfiguration();
        BgpSecCertificate certificate = addBgpSecEntity(CertificateStatus.CURRENT);

        assertThat(subject.findCurrentConfigurationDataByCaId(ca.getId(), configurationId)).hasValueSatisfying(data -> {
            assertThat(data.id()).isEqualTo(configurationId);
            assertThat(data.notValidBefore().getMillis()).isEqualTo(certificate.getValidityPeriod().getNotValidBefore().getMillis());
            assertThat(data.notValidAfter().getMillis()).isEqualTo(certificate.getValidityPeriod().getNotValidAfter().getMillis());
        });
    }

    @Test
    public void findCurrentConfigurationDataByCaId_doesNotMatchEntityOfAnotherCa() {
        long configurationId = addConfiguration();

        // Another CA happens to have an entity with the same ASN/router id/key identifier
        HostedCertificateAuthority otherCa = new HostedCertificateAuthority(
                ca.getId() + 1, new X500Principal("CN=other-ca"), UUID.randomUUID(), ca);
        issueCertificateForNewKey(ca, otherCa, ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES);
        KeyPairEntity otherKp = otherCa.getCurrentKeyPair();
        BgpSecCertificate otherCertificate = createBgpSecCertificateFromCSR(
                otherKp, Csr.getPublicKey(CSR), ASN, 1L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
        bgpSecCertificateRepository.add(otherCertificate);
        bgpSecEntityRepository.add(new BgpSecEntity(ASN, Csr.getKeyIdentifier(CSR), ROUTER_ID, otherCertificate, "bgpsec-1.cer", DIRECTORY));
        entityManager.flush();

        assertThat(subject.findCurrentConfigurationDataByCaId(ca.getId(), configurationId)).hasValueSatisfying(data -> {
            assertThat(data.notValidBefore()).isNull();
            assertThat(data.notValidAfter()).isNull();
        });
    }

    private long addConfiguration() {
        BgpSecConfiguration configuration = new BgpSecConfiguration(ca, ASN, ROUTER_ID, CSR);
        subject.add(configuration);
        entityManager.flush();
        return configuration.getId();
    }

    private BgpSecCertificate addBgpSecEntity(CertificateStatus status) {
        KeyPairEntity kp = ca.getCurrentKeyPair();
        BgpSecCertificate certificate = createBgpSecCertificateFromCSR(
                kp, Csr.getPublicKey(CSR), ASN, 1L, DateTime.now(), DateTime.now().plusYears(1), status);
        bgpSecCertificateRepository.add(certificate);
        bgpSecEntityRepository.add(new BgpSecEntity(ASN, Csr.getKeyIdentifier(CSR), ROUTER_ID, certificate, "bgpsec-1.cer", DIRECTORY));
        entityManager.flush();
        return certificate;
    }
}
