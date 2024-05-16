package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static net.ripe.rpki.commons.validation.roa.RouteValidityState.*;
import static org.junit.Assert.assertEquals;

@Transactional
public class JpaRoaAlertConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private RoaAlertConfigurationRepository subject;

    @Before
    public void setUp() {
        clearDatabase();
        ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources();
        entityManager.persist(ca);
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

    @Test
    public void shouldFindByEmail() {
        List<RoaAlertConfiguration> all = subject.findAll().stream().collect(Collectors.toList());
        var c = subject.findByEmail(all.get(0).getSubscriptionOrNull().getEmails().get(0));
        assertEquals(c, all);
    }
}
