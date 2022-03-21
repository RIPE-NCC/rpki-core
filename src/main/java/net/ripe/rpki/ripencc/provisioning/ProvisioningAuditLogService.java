package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;

import java.util.List;
import java.util.UUID;

public interface ProvisioningAuditLogService {

    void log(ProvisioningAuditLogEntity entry, byte[] request);

    List<ProvisioningAuditData> findRecentMessagesForCA(UUID caUUID);
}
