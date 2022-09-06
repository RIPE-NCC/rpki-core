package net.ripe.rpki.util;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthorityTest;
import net.ripe.rpki.domain.TestServices;
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

import static org.assertj.core.api.Assertions.assertThat;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class JdbcDBComponentTest extends CertificationDomainTestCase {

    @Autowired
    private JdbcDBComponent jdbcDbComponent;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> {
            clearDatabase();
            ProductionCertificateAuthority ca = ProductionCertificateAuthorityTest.createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
            certificateAuthorityRepository.add(ca);
        });
    }

    @After
    public void tearDown() {
        transactionTemplate.executeWithoutResult((status) -> clearDatabase());
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
            HostedCertificateAuthority childCa = new HostedCertificateAuthority(parentCa.getId() + 1, new X500Principal("CN=child"), parentCa);
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
            ca.roaConfigurationUpdated(); // Force state change in CA so Hibernate will flush entity
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

            ca.roaConfigurationUpdated(); // Force state change in CA so Hibernate will flush entity
            entityManager.flush();

            // After flush the lock type changes to OPTIMISTIC_FORCE_INCREMENT
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.OPTIMISTIC_FORCE_INCREMENT);

            // But explicit locking changes it back to PESSIMISTIC_FORCE_INCREMENT
            jdbcDbComponent.lockCertificateAuthorityForceIncrement(ca.getId());
            assertThat(entityManager.getLockMode(ca)).isEqualTo(LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        });
    }

}
