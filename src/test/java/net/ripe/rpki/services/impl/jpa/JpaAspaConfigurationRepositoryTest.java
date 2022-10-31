package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class JpaAspaConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private JpaAspaConfigurationRepository subject;

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
        SortedMap<Asn, AspaAfiLimit> providers = new TreeMap<>();
        providers.put(Asn.parse("AS10"), AspaAfiLimit.ANY);
        providers.put(Asn.parse("AS11"), AspaAfiLimit.IPv4);
        providers.put(Asn.parse("AS12"), AspaAfiLimit.IPv6);
        final AspaConfiguration aspa1 = new AspaConfiguration(ca, Asn.parse("AS1"), providers);
        subject.add(aspa1);
        final SortedMap<Asn, AspaConfiguration> byCa = subject.findByCertificateAuthority(ca);
        assertThat(byCa.values()).hasSize(1).allSatisfy(aspa -> assertThat(aspa.toData()).isEqualTo(aspa1.toData()));
    }
}
