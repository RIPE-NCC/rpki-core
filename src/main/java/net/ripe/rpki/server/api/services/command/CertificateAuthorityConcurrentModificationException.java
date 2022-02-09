package net.ripe.rpki.server.api.services.command;

import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CommandAuditData;

import java.util.List;

/**
 * Indicates that the CertificateAuthority in the command was modified by another command.
 */
public class CertificateAuthorityConcurrentModificationException extends CertificationException {

    private static final long serialVersionUID = 1L;

    private final CertificateAuthorityCommand command;

    private final long currentCertificateAuthorityVersion;

    private final List<CommandAuditData> conflictingCommands;

    public CertificateAuthorityConcurrentModificationException(CertificateAuthorityCommand command, long currentCertificateAuthorityVersion,
            List<CommandAuditData> conflictingCommands) {
        super(getMessage(command, currentCertificateAuthorityVersion, conflictingCommands));
        this.command = command;
        this.currentCertificateAuthorityVersion = currentCertificateAuthorityVersion;
        this.conflictingCommands = conflictingCommands;
    }

    private static String getMessage(CertificateAuthorityCommand command, long currentCertificateAuthorityVersion, List<CommandAuditData> conflictingCommands) {
        return "CA id = " + command.getCertificateAuthorityVersionedId().getId() +
                ", current version = " + currentCertificateAuthorityVersion +
                ", command version = " + command.getCertificateAuthorityVersionedId().getVersion() + "" +
                ", command = " + command +
                ", conflictingCommands = " + conflictingCommands;
    }

    public long getCertificateAuthorityId() {
        return command.getCertificateAuthorityVersionedId().getId();
    }

    public CertificateAuthorityCommand getCommand() {
        return command;
    }

    public long getCurrentCertificateAuthorityVersion() {
        return currentCertificateAuthorityVersion;
    }

    public List<CommandAuditData> getConflictingCommands() {
        return conflictingCommands;
    }

}
