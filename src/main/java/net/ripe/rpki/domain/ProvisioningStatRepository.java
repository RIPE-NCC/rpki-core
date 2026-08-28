package net.ripe.rpki.domain;

import net.ripe.rpki.ripencc.provisioning.ProvisioningException;

import java.time.Instant;
import java.util.UUID;

public interface ProvisioningStatRepository {
    void track(ProvisioningAuditLogEntity logEntry);
    void trackProvisioningError(UUID nonHostedCaUUID, Instant timestamp, ProvisioningException error);
}
