package net.ripe.rpki.domain.audit;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CommandAuditData;

import java.util.List;

public interface CommandAuditService {

    void record(CertificateAuthorityCommand command, VersionedId caId);

    /**
     * Find the most recent 'User' commands; i.e. commands executed by hand, not by background services in the system
     */
    List<CommandAuditData> findMostRecentUserCommandsForCa(long caId);

    List<CommandAuditData> findCommandsSinceCaVersion(VersionedId caId);

    /**
     * Used when we delete a CA completely.
     */
    void deleteCommandsForCa(long caId);
}
