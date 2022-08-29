package net.ripe.rpki.util;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
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

import javax.persistence.LockModeType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class JdbcDBComponentTest extends CertificationDomainTestCase {

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
    public void should_lock_with_force_increment() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

    @Test
    public void should_still_be_locked_after_entity_manager_flush() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            ca.manifestAndCrlCheckCompleted();
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
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertEquals(LockModeType.OPTIMISTIC, entityManager.getLockMode(ca));

            ca.manifestAndCrlCheckCompleted();
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT, which we should still consider "locked"
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.OPTIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            // But explicit locking changes it back to PESSIMISTIC_FORCE_INCREMENT
            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            assertTrue(jdbcDbComponent.isLocked(ca));
            assertEquals(LockModeType.PESSIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(ca));

            status.setRollbackOnly();
        });
    }

}
