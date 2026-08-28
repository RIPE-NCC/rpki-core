package net.ripe.rpki.services.impl.jpa;

import jakarta.persistence.EntityManager;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.domain.ProvisioningStatRecord;
import net.ripe.rpki.ripencc.provisioning.ProvisioningException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaProvisioningStatRepositoryTest extends CertificationDomainTestCase {
    @Autowired
    private JpaProvisioningStatRepository subject;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void it_tracks_first_success_message() {
        var logEntry = fakeLogEntry(new ResourceClassListQueryPayloadBuilder().build());
        subject.track(logEntry);

        var stat = getStat(logEntry.getNonHostedCaUUID());
        assertThat(stat.getLastSuccess()).isEqualTo(Optional.of(logEntry.getExecutionTime().toInstant()));
        assertThat(stat.getLastFailure()).isEmpty();
    }

    @Test
    @Transactional
    void it_tracks_update_on_existing_stat() {
        var logEntry = fakeLogEntry(new ResourceClassListQueryPayloadBuilder().build());
        var existing = new ProvisioningStatRecord(logEntry.getNonHostedCaUUID());
        existing.setLastSuccess(Instant.EPOCH);
        entityManager.persist(existing);
        subject.track(logEntry);

        var stat = getStat(logEntry.getNonHostedCaUUID());
        assertThat(stat.getLastSuccess()).isEqualTo(Optional.of(logEntry.getExecutionTime().toInstant()));
        assertThat(stat.getLastFailure()).isEmpty();
    }

    @Test
    @Transactional
    void it_tracks_request_not_performed_as_failure() {
        var reason = "Processing error";
        var payload = new RequestNotPerformedResponsePayloadBuilder();
        payload.withError(NotPerformedError.INTERNAL_SERVER_ERROR);
        payload.withDescription(reason);
        var logEntry = fakeLogEntry(payload.build());
        entityManager.persist(new ProvisioningStatRecord(logEntry.getNonHostedCaUUID()));
        subject.track(logEntry);

        var stat = getStat(logEntry.getNonHostedCaUUID());
        assertThat(stat.getLastFailure()).isEqualTo(Optional.of(Pair.of(logEntry.getExecutionTime().toInstant(), logEntry.getSummary())));
        assertThat(stat.getLastSuccess()).isEmpty();
    }

    @Test
    @Transactional
    void it_does_not_touch_last_failure_on_tracking_success() {
        var logEntry = fakeLogEntry(new ResourceClassListQueryPayloadBuilder().build());
        var existing = new ProvisioningStatRecord(logEntry.getNonHostedCaUUID());
        existing.setLastSuccess(Instant.EPOCH);
        existing.setLastFailure(Instant.now().minusMillis(24 * 3_600_000L), "Some failure reason.");
        entityManager.persist(existing);

        subject.track(logEntry);

        var stat = getStat(logEntry.getNonHostedCaUUID());
        assertThat(stat.getLastSuccess()).isEqualTo(Optional.of(logEntry.getExecutionTime().toInstant()));
        assertThat(stat.getLastFailure()).isEqualTo(existing.getLastFailure());
    }

    @Test
    @Transactional
    void it_tracks_failure_for_provisioning_errors() {
        var sender = UUID.randomUUID();
        var timestamp = Instant.now();
        var error = new ProvisioningException.BadData();
        subject.trackProvisioningError(sender, timestamp, error);

        var stat = getStat(sender);
        assertThat(stat.getLastFailure()).isEqualTo(Optional.of(Pair.of(timestamp, error.getName())));
    }

    private static ProvisioningAuditLogEntity fakeLogEntry(AbstractProvisioningPayload payload) {
        var cms = mock(ProvisioningCmsObject.class);
        when(cms.getPayload()).thenReturn(payload);
        return new ProvisioningAuditLogEntity(cms, "principal", UUID.randomUUID());
    }

    private ProvisioningStatRecord getStat(UUID sender) {
        return entityManager.createQuery("FROM ProvisioningStatRecord WHERE nonHostedCaUUID = :uuid", ProvisioningStatRecord.class)
            .setParameter("uuid", sender)
            .getSingleResult();
    }
}
