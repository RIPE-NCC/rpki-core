package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;

import java.util.List;

public interface ProvisioningAuditLogService {

    void log(ProvisioningAuditLogEntity entry, byte[] request);

    List<ProvisioningAuditData> findRecentMessagesForCA(String caUUID);
}
