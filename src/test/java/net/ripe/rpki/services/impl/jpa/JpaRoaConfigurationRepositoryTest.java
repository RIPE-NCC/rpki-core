package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.transaction.Transactional;
import org.springframework.test.annotation.Commit;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Transactional
@Commit // don't rollback, we want all constrains to be checked
public class JpaRoaConfigurationRepositoryTest extends CertificationDomainTestCase {

    @Autowired
    private RoaConfigurationRepository subject;

    private ProductionCertificateAuthority ca;

    @Before
    public void setUp() {
        clearDatabase();
        ca = createInitialisedProdCaWithRipeResources();
        entityManager.persist(ca);
    }

    @Test
    public void getOrCreateByCertificateAuthority() {
        RoaConfiguration roaConfiguration1 = subject.getOrCreateByCertificateAuthority(ca);
        assertNotNull(roaConfiguration1);

        RoaConfiguration roaConfiguration2 = subject.getOrCreateByCertificateAuthority(ca);
        assertNotNull(roaConfiguration2);

        assertEquals(roaConfiguration1.getId(), roaConfiguration2.getId());
        assertEquals(roaConfiguration1.getCreatedAt(), roaConfiguration2.getCreatedAt());
        assertEquals(roaConfiguration1.getUpdatedAt(), roaConfiguration2.getUpdatedAt());
        assertEquals(roaConfiguration1.getCertificateAuthority().getId(), roaConfiguration2.getCertificateAuthority().getId());
    }

    @Test
    public void shouldFindAllPrefixesAndCountPrefixes() {
        RoaConfiguration roaConfig = subject.getOrCreateByCertificateAuthority(ca);
        RoaConfigurationPrefix p1 = new RoaConfigurationPrefix(new Asn(1), IpRange.parse("10.11.0.0/16"), 16);
        RoaConfigurationPrefix p2 = new RoaConfigurationPrefix(new Asn(2), IpRange.parse("10.12.0.0/16"), 16);
        RoaConfigurationPrefix p3 = new RoaConfigurationPrefix(new Asn(3), IpRange.parse("10.13.0.0/16"), null);
        subject.addPrefixes(roaConfig, Arrays.asList(p1, p2, p3));
        var allPerCa = subject.findAllPrefixes();
        assertNotNull(allPerCa);
        assertEquals(3, allPerCa.size());
        assertEquals(3, subject.countRoaPrefixes());
    }

    @Test
    public void shouldReturnEmptyLastModifiedWhenEmpty() {
        then(subject.findAll()).hasSize(0);
        then(subject.lastModified()).isEmpty();
    }

    @Test
    public void shouldSetRecentLastModified() {
        // Insert a number of rows
        RoaConfiguration roaConfig = subject.getOrCreateByCertificateAuthority(ca);
        RoaConfigurationPrefix p1 = new RoaConfigurationPrefix(new Asn(1), IpRange.parse("10.11.0.0/16"), 16);
        RoaConfigurationPrefix p2 = new RoaConfigurationPrefix(new Asn(2), IpRange.parse("10.12.0.0/16"), 16);
        RoaConfigurationPrefix p3 = new RoaConfigurationPrefix(new Asn(3), IpRange.parse("10.13.0.0/16"), null);
        roaConfig.addPrefixes(Arrays.asList(p1, p2, p3));

        // And check not modified
        then(subject.lastModified().get()).isAfterOrEqualTo(Instant.ofEpochMilli(roaConfig.getUpdatedAt().getMillis()));
    }

    /**
     * Log a deletion and check that the deletion updates the last modified time.
     */
    @Test
    public void shouldSetRecentLastModifiedForDeletes() {
        RoaConfiguration roaConfig = subject.getOrCreateByCertificateAuthority(ca);
        transactionTemplate.execute((status) -> entityManager.merge(roaConfig));

        // And check that not modified has updated
        then(subject.lastModified().get()).isAfterOrEqualTo(Instant.ofEpochMilli(roaConfig.getUpdatedAt().toInstant().getMillis()));
    }

    @Test
    public void shouldInsertDeletedPrefixesToSeparateTable() {
        RoaConfiguration roaConfig = subject.getOrCreateByCertificateAuthority(ca);
        RoaConfigurationPrefix p1 = new RoaConfigurationPrefix(Asn.parse("AS10"), IpRange.parse("20.0.0.0/8"));
        RoaConfigurationPrefix p2 = new RoaConfigurationPrefix(Asn.parse("AS11"), IpRange.parse("21.21.0.0/16"));
        RoaConfigurationPrefix p3 = new RoaConfigurationPrefix(Asn.parse("AS12"), IpRange.parse("2a03:600::/32"));

        final List<RoaConfigurationPrefix> prefixes = Arrays.asList(p1, p2, p3);
        subject.addPrefixes(roaConfig, prefixes);
        subject.removePrefixes(roaConfig, prefixes);

        assertEquals(3L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes WHERE asn = 10 AND prefix_type_id = 1 AND maximum_length = 8"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes WHERE asn = 11 AND prefix_type_id = 1 AND maximum_length = 16"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes WHERE asn = 12 AND prefix_type_id = 2 AND maximum_length = 32"));
    }

    @Test
    public void shouldInsertDeletedPrefixesToSeparateTableWhenUpdatingPrefix() {
        RoaConfiguration roaConfig = subject.getOrCreateByCertificateAuthority(ca);
        RoaConfigurationPrefix p1 = new RoaConfigurationPrefix(Asn.parse("AS10"), IpRange.parse("20.0.0.0/8"));
        RoaConfigurationPrefix p2 = new RoaConfigurationPrefix(Asn.parse("AS11"), IpRange.parse("21.21.0.0/16"));
        RoaConfigurationPrefix p3 = new RoaConfigurationPrefix(Asn.parse("AS12"), IpRange.parse("2a03:600::/32"));

        subject.addPrefixes(roaConfig, Arrays.asList(p1, p2, p3));

        assertEquals(3L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 10 AND maximum_length = 8"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 11"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 12"));

        // replace max length 8 with 12
        subject.mergePrefixes(roaConfig,
                List.of(new RoaConfigurationPrefix(Asn.parse("AS10"), IpRange.parse("20.0.0.0/8"), 12)),
                List.of(new RoaConfigurationPrefix(Asn.parse("AS10"), IpRange.parse("20.0.0.0/8"), 8)));

        assertEquals(3L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 10 AND maximum_length = 12"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 11"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM roaconfiguration_prefixes WHERE asn = 12"));

        assertEquals(1L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes"));
        assertEquals(1L, countQuery("SELECT COUNT(*) FROM deleted_roaconfiguration_prefixes WHERE asn = 10 AND prefix_type_id = 1 AND maximum_length = 8"));
    }

    long countQuery(String sql) {
        return (Long) entityManager
                .createNativeQuery(sql)
                .getSingleResult();
    }

}