package net.ripe.rpki.util;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;

@Primary
@Component
@Slf4j
public class JdbcDBComponent implements DBComponent {

    private final EntityManager entityManager;

    @Autowired
    public JdbcDBComponent(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static void afterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    @Override
    public Long lockCertificateAuthorityForUpdate(long caId) {
        return lockCertificateAuthorityForMode(caId, "UPDATE");
    }

    @Override
    public Long lockCertificateAuthorityForSharing(long caId) {
        return lockCertificateAuthorityForMode(caId, "SHARE");
    }

    @Override
    public void lockCertificateAuthorityForceIncrement(long caId) {
        entityManager.find(CertificateAuthority.class, caId, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    private Long lockCertificateAuthorityForMode(long caId, String mode) {
        log.debug("Attempting to lock CA (id = {}) for {}}", caId, mode);
        try {
            // Only lock the row and do not retrieve the CA to avoid eagerly loading the parent CA (we might need to
            // lock the parent before loading it to avoid optimistic locking exceptions due to transactions committing
            // after loading the parent but before locking it).
            Number parentId = (Number) entityManager.createNativeQuery(
                    "SELECT parent_id FROM certificateauthority ca WHERE ca.id = :id FOR " + mode
                )
                .setParameter("id", caId)
                .getSingleResult();
            log.debug("Locked CA (id = {}) for {}", caId, mode);
            return parentId == null ? null : parentId.longValue();
        } catch (NoResultException e) {
            log.debug("CA (id = {}) does not exist, lock for {} failed", caId, mode);
            return null;
        }
    }

}
