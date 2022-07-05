package net.ripe.rpki.util;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.Entity;
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
        log.debug("Attempting to lock CA (id = {})", caId);
        try {
            // Only lock the row and do not retrieve the CA to avoid eagerly loading the parent CA (we might need to
            // lock the parent before loading it to avoid optimistic locking exceptions due to transactions committing
            // after loading the parent but before locking it).
            Number parentId = (Number) entityManager.createNativeQuery("SELECT parent_id FROM certificateauthority ca WHERE ca.id = :id FOR UPDATE")
                .setParameter("id", caId)
                .getSingleResult();
            log.debug("Locked CA (id = {})", caId);
            return parentId == null ? null : parentId.longValue();
        } catch (NoResultException e) {
            log.debug("CA (id = {}) does not exist, lock failed", caId);
            return null;
        }
    }

    @Override
    public void lockCertificateAuthorityForceIncrement(long caId) {
        entityManager.find(CertificateAuthority.class, caId, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    boolean isLocked(Entity entity) {
        // We cannot compare only with PESSIMISTIC_WRITE here, since the lock mode can change due to
        // JPA operations (e.g. it can change to OPTIMISTIC_FORCE_INCREMENT when entity properties are
        // changed and the entity is written to the database).
        LockModeType lockMode = entityManager.getLockMode(entity);
        return lockMode == LockModeType.PESSIMISTIC_WRITE
            || lockMode == LockModeType.PESSIMISTIC_FORCE_INCREMENT
            || lockMode == LockModeType.OPTIMISTIC_FORCE_INCREMENT;
    }

}
