package net.ripe.rpki.services.impl.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.domain.ProvisioningStatRecord;
import net.ripe.rpki.domain.ProvisioningStatRepository;
import net.ripe.rpki.ripencc.provisioning.ProvisioningException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public class JpaProvisioningStatRepository implements ProvisioningStatRepository {
    private final EntityManager entityManager;

    public JpaProvisioningStatRepository(EntityManager em) {
        this.entityManager = em;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void track(ProvisioningAuditLogEntity logEntry) {
        var statRecord = getByNonHostedCA(logEntry.getNonHostedCaUUID());
        if (logEntry.getRequestMessageType() != PayloadMessageType.error_response) {
            statRecord.setLastSuccess(logEntry.getExecutionTime().toInstant());
        } else {
            statRecord.setLastFailure(logEntry.getExecutionTime().toInstant(), logEntry.getSummary());
        }
        entityManager.merge(statRecord);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void trackProvisioningError(UUID nonHostedCaUUID, Instant timestamp, ProvisioningException error) {
        var statRecord = getByNonHostedCA(nonHostedCaUUID);
        statRecord.setLastFailure(timestamp, error.getName());
        entityManager.merge(statRecord);
    }

    private ProvisioningStatRecord getByNonHostedCA(UUID nonHostedCaUUID) {
        try {
            return entityManager.createQuery("FROM ProvisioningStatRecord WHERE nonHostedCaUUID = :uuid", ProvisioningStatRecord.class)
                .setParameter("uuid", nonHostedCaUUID)
                .getSingleResult();
        } catch (NoResultException e) {
            var stat = new ProvisioningStatRecord(nonHostedCaUUID);
            entityManager.persist(stat);
            return stat;
        }
    }
}
