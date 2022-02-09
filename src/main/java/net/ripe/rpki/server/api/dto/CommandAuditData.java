package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import org.joda.time.DateTime;

public class CommandAuditData extends CertificateAuthorityHistoryItem {

    private static final long serialVersionUID = 1L;

    private final VersionedId certificateAuthorityVersionedId;
    private final String commandType;
    private final String commandGroup;

    public CommandAuditData(DateTime executionTime, VersionedId certificateAuthorityVersionedId, String principal, String commandType, CertificateAuthorityCommandGroup commandGroup, String commandSummary) {
        super(executionTime, principal, commandSummary);
        this.certificateAuthorityVersionedId = certificateAuthorityVersionedId;
        this.commandType = commandType;
        this.commandGroup = commandGroup.name();
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return certificateAuthorityVersionedId;
    }

    public long getCertificateAuthorityId() {
        return certificateAuthorityVersionedId.getId();
    }

    public long getCertificateAuthorityVersion() {
        return certificateAuthorityVersionedId.getVersion();
    }

    public String getCommandType() {
        return commandType;
    }

    public CertificateAuthorityCommandGroup getCommandGroup() {
        return CertificateAuthorityCommandGroup.valueOf(commandGroup);
    }

}
