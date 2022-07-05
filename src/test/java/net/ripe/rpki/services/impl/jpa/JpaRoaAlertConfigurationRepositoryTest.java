package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static net.ripe.rpki.commons.validation.roa.RouteValidityState.INVALID_ASN;
import static net.ripe.rpki.commons.validation.roa.RouteValidityState.INVALID_LENGTH;
import static net.ripe.rpki.commons.validation.roa.RouteValidityState.UNKNOWN;
import static org.junit.Assert.assertEquals;

@Transactional
public class JpaRoaAlertConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private RoaAlertConfigurationRepository subject;

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
        RoaAlertConfiguration weekly = new RoaAlertConfiguration(ca, "weekly@alert", Arrays.asList(INVALID_ASN, INVALID_LENGTH, UNKNOWN), RoaAlertFrequency.WEEKLY);
        subject.add(weekly);
    }

    @Test
    public void shouldFindByWeeklyFrequency() {
        List<RoaAlertConfiguration> weekly = subject.findByFrequency(RoaAlertFrequency.WEEKLY);
        assertEquals(1, weekly.size());
    }

    @Test
    public void shouldFindAll() {
        Collection<RoaAlertConfiguration> all = subject.findAll();
        assertEquals(1, all.size());
    }
}
