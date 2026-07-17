package net.ripe.rpki.services.impl.jpa;

import jakarta.transaction.Transactional;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Rollback
public class JpaBgpSecConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private JpaBgpSecConfigurationRepository subject;

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
}
