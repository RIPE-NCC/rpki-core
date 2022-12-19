package net.ripe.rpki.util;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.LockModeType;
import javax.security.auth.x500.X500Principal;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Isolation.REPEATABLE_READ;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class JdbcDBComponentTest extends CertificationDomainTestCase {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcDBComponent jdbcDbComponent;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> {
            clearDatabase();
            ProductionCertificateAuthority ca = TestObjects.createInitialisedProdCaWithRipeResources();
            certificateAuthorityRepository.add(ca);
        });
    }

    @After
    public void tearDown() {
        transactionTemplate.executeWithoutResult((status) -> clearDatabase());
    }

    @Test
    public void should_have_repeatable_read_as_transaction_isolation_level() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getTransactionIsolation()).as("transaction isolation level").isEqualTo(REPEATABLE_READ.value());
        }
    }

    @Test
    public void should_lock_certificate_authority_without_loading() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            entityManager.clear();

            Long parentId = jdbcDbComponent.lockCertificateAuthorityForSharing(ca.getId());
            assertThat(parentId).isNull();
            assertThat(entityManager.contains(ca)).as("entity contained in session after lock for share").isFalse();

            parentId = jdbcDbComponent.lockCertificateAuthorityForUpdate(ca.getId());
            assertThat(parentId).isNull();
            assertThat(entityManager.contains(ca)).as("entity contained in session after lock for update").isFalse();
        });
    }

    @Test
    public void should_lock_certificate_authority_and_return_parent_id() {
        transactionTemplate.executeWithoutResult((status) -> {
            ManagedCertificateAuthority parentCa = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            HostedCertificateAuthority childCa = new HostedCertificateAuthority(parentCa.getId() + 1, new X500Principal("CN=child"), UUID.randomUUID(), parentCa);
            entityManager.persist(childCa);

            Long parentId = jdbcDbComponent.lockCertificateAuthorityForSharing(childCa.getId());
            assertThat(parentId).isEqualTo(parentCa.getId());

            parentId = jdbcDbComponent.lockCertificateAuthorityForUpdate(childCa.getId());
            assertThat(parentId).isEqualTo(parentCa.getId());
        });
    }

    @Test
    public void should_ignore_lock_for_nonexistent_certificate_authority() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();

            Long parentId = jdbcDbComponent.lockCertificateAuthorityForSharing(ca.getId() + 1);
            assertThat(parentId).isNull();
        });
    }

    @Test
    public void should_lock_with_force_increment() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC);

            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        });
    }

    @Test
    public void should_still_be_locked_after_entity_manager_flush() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC);

            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            ca.configurationUpdated(); // Force state change in CA so Hibernate will flush entity
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        });
    }

    @Test
    public void should_still_lock_after_entity_manager_flush() {
        transactionTemplate.executeWithoutResult((status) -> {
            final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC);

            ca.configurationUpdated(); // Force state change in CA so Hibernate will flush entity
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC_FORCE_INCREMENT);

            // But explicit locking changes it back to PESSIMISTIC_FORCE_INCREMENT
            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        });
    }

    @Test
    public void should_be_fair_when_locking_for_update_while_locked_for_sharing() throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long caId = transactionTemplate.execute((status) -> {
                final ManagedCertificateAuthority ca = (ManagedCertificateAuthority) certificateAuthorityRepository.findAll().iterator().next();
                return ca.getId();
            });

            // Default PostgreSQL row lock (SELECT ... FOR SHARE/UPDATE) is not fair w.r.t. shared vs exclusive.
            // A thread trying to get an exclusive lock will wait indefinitely if it keeps getting locked for
            // sharing by other threads.
            CountDownLatch thread1_lockShared = new CountDownLatch(1);
            CountDownLatch thread1_doUnlock = new CountDownLatch(1);
            CountDownLatch thread2_tryLockExclusive = new CountDownLatch(1);
            CountDownLatch thread2_lockExclusive = new CountDownLatch(1);
            CountDownLatch thread2_doUnlock = new CountDownLatch(1);
            CountDownLatch thread3_tryLockShared = new CountDownLatch(1);
            CountDownLatch thread3_lockShared = new CountDownLatch(1);
            CountDownLatch thread3_doUnlock = new CountDownLatch(1);

            pool.execute(() -> transactionTemplate.executeWithoutResult((status) -> {
                try {
                    jdbcDbComponent.lockCertificateAuthorityForSharing(caId);
                    thread1_lockShared.countDown();
                    thread1_doUnlock.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));

            // Wait for thread 1 to hold the shared lock
            assertThat(thread1_lockShared.await(1, TimeUnit.SECONDS)).isTrue();

            pool.execute(() -> transactionTemplate.executeWithoutResult((status) -> {
                try {
                    thread2_tryLockExclusive.countDown();
                    jdbcDbComponent.lockCertificateAuthorityForUpdate(caId);
                    thread2_lockExclusive.countDown();
                    thread2_doUnlock.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));

            // Wait for thread2 to try to get an exclusive lock
            assertThat(thread2_tryLockExclusive.await(1, TimeUnit.SECONDS)).isTrue();

            pool.execute(() -> transactionTemplate.executeWithoutResult((status) -> {
                try {
                    thread3_tryLockShared.countDown();
                    jdbcDbComponent.lockCertificateAuthorityForSharing(caId);
                    thread3_lockShared.countDown();
                    thread3_doUnlock.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));

            // Wait for thread3 to try to get a shared lock
            assertThat(thread3_tryLockShared.await(1, TimeUnit.SECONDS)).isTrue();

            // Thread 3 should not get the shared lock, since thread 2 is waiting for an exclusive lock
            assertThat(thread3_lockShared.await(100, TimeUnit.MILLISECONDS)).isFalse();

            // Release the shared lock
            thread1_doUnlock.countDown();

            // Thread 2 should get an exclusive lock
            assertThat(thread2_lockExclusive.await(1, TimeUnit.SECONDS)).isTrue();

            // Release the exclusive lock
            thread2_doUnlock.countDown();

            // Thread 3 should get the shared lock, since thread 2 no longer holds an exclusive lock
            assertThat(thread3_lockShared.await(1, TimeUnit.SECONDS)).isTrue();

            // Release the shared lock
            thread3_doUnlock.countDown();
        } finally {
            pool.shutdownNow();
        }
    }

}
