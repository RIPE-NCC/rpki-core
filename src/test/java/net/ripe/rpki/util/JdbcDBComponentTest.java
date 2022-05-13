package net.ripe.rpki.util;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthorityTest;
import net.ripe.rpki.domain.TestServices;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class JdbcDBComponentTest extends CertificationDomainTestCase {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcDBComponent jdbcDbComponent;

    @Autowired
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ProductionCertificateAuthority ca;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> {
            clearDatabase();
            ca = ProductionCertificateAuthorityTest.createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
            certificateAuthorityRepository.add(ca);
        });
    }

    @Test
    public void should_lock_and_refresh_with_pessimistic_write_lock() {
        transactionTemplate.executeWithoutResult((status) -> {
            final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            ca.setLastIssuedSerial(BigInteger.valueOf(444444));
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            jdbcDbComponent.lockAndRefresh(ca);
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            ca.setLastIssuedSerial(BigInteger.valueOf(555555));
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

    @Test
    public void should_lock_with_pessimistic_write_lock() {
        transactionTemplate.executeWithoutResult((status) -> {
            final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            ca.setLastIssuedSerial(BigInteger.valueOf(444444));
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            jdbcDbComponent.lock(ca);
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

    @Test
    public void should_still_be_locked_after_entity_manager_flush() {
        transactionTemplate.executeWithoutResult((status) -> {
            final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            jdbcDbComponent.lock(ca);
            ca.setLastIssuedSerial(BigInteger.valueOf(444444));
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT, which we should still consider "locked"
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.OPTIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

    @Test
    public void should_still_lock_after_entity_manager_flush() {
        transactionTemplate.executeWithoutResult((status) -> {
            final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            ca.setLastIssuedSerial(BigInteger.valueOf(444444));
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT, which we should still consider "locked"
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.OPTIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            // But explicit locking changes it back to PESSIMISTIC_WRITE again...
            jdbcDbComponent.lock(ca);
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

    @Test
    @Transactional
    public void nextSerial() {
        final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
        jdbcDbComponent.lock(ca);
        final BigInteger bigInteger = jdbcDbComponent.nextSerial(ca);
        final HostedCertificateAuthority caUpdated = (HostedCertificateAuthority) certificateAuthorityRepository.find(ca.getId());
        assertEquals(caUpdated.getLastIssuedSerial(), bigInteger);
    }

    @Test
    @Transactional
    public void nextSerial_should_fail_when_ca_lastIssuedSerial_was_modified_in_database() {
        final HostedCertificateAuthority ca = (HostedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
        entityManager.createNativeQuery("UPDATE certificateauthority SET last_issued_serial = last_issued_serial + 1 WHERE id = :id")
            .setParameter("id", ca.getId())
            .executeUpdate();
        jdbcDbComponent.lock(ca);

        assertThrows(IllegalStateException.class, () -> {
            jdbcDbComponent.nextSerial(ca);
        });
    }
}
