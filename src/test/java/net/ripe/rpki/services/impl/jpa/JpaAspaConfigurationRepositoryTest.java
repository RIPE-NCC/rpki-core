package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@Transactional
public class JpaAspaConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private JpaAspaConfigurationRepository subject;

    @Autowired
    private EntityManager entityManager;

    private ProductionCertificateAuthority ca;

    @Autowired
    private CertificateAuthorityRepository caRepository;

    @Before
    public void setUp() {
        clearDatabase();
        ca = createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        caRepository.add(ca);
        entityManager.flush();
    }

    @Test
    public void shouldReturnEmpty() {
        assertFalse(subject.findByCertificateAuthority(ca).isPresent());
    }

    @Test
    public void shouldCreateAndGetBack() {
        final AspaConfiguration aspa1 = new AspaConfiguration(ca, BigInteger.valueOf(1), Collections.emptySet());
        subject.add(aspa1);
        entityManager.flush();
        final Optional<AspaConfiguration> byCa = subject.findByCertificateAuthority(ca);
        assertEquals(aspa1.toData(), byCa.get().toData());
    }

}