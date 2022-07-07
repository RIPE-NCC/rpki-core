package net.ripe.rpki.domain.audit;

import lombok.NonNull;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.dto.CommandAuditData;

import java.util.List;

public interface CommandAuditService {

    @NonNull CommandContext startRecording(CertificateAuthorityCommand command);

    void finishRecording(CommandContext recording);

    /**
     * Find the most recent 'User' commands; i.e. commands executed by hand, not by background services in the system
     */
    List<CommandAuditData> findMostRecentCommandsForCa(long caId);

    List<CommandAuditData> findCommandsSinceCaVersion(VersionedId caId);

    /**
     * Used when we delete a CA completely.
     */
    void deleteCommandsForCa(long caId);
}
