package net.ripe.rpki.util;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.Entity;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import java.math.BigDecimal;
import java.math.BigInteger;

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
    public void lock(Entity entity) {
        entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        log.debug("locked {} with lock mode {}", entity, entityManager.getLockMode(entity));
    }

    @Override
    public void lockAndRefresh(Entity entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        log.debug("locked and refreshed {} with lock mode {}", entity, entityManager.getLockMode(entity));
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

    @Override
    public BigInteger nextSerial(HostedCertificateAuthority ca) {
        // The caller must lock the CA before asking for the next serial. We do not lock here because we do not know
        // if we just need to lock or need to lock+refresh+recheck, so the caller must decide this.
        Validate.isTrue(isLocked(ca), "CA must be locked before issuing new serial");
        try {
            final Object serial = entityManager.createNativeQuery("UPDATE certificateauthority " +
                "SET last_issued_serial = last_issued_serial + FLOOR(RANDOM() * random_serial_increment + 1)\\:\\:int " +
                "WHERE id = :caId AND last_issued_serial = :lastIssuedSerial " +
                "RETURNING last_issued_serial")
                .setParameter("caId", ca.getId())
                .setParameter("lastIssuedSerial", ca.getLastIssuedSerial())
                .setFlushMode(FlushModeType.COMMIT) // No need for dirty checking
                .getSingleResult();

            final BigInteger nextSerial = ((BigDecimal) serial).toBigInteger();
            ca.setLastIssuedSerial(nextSerial);
            return nextSerial;
        } catch (NoResultException e) {
            throw new IllegalStateException("failed to issue new serial for CA " + ca.getName(), e);
        }
    }

}
