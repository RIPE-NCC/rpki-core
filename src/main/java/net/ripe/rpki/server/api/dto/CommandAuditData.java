package net.ripe.rpki.server.api.dto;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

public class CommandAuditData extends CertificateAuthorityHistoryItem {

    private static final long serialVersionUID = 1L;

    @Getter
    private final VersionedId certificateAuthorityVersionedId;
    @Getter
    private final String commandType;
    private final String commandGroup;
    @Getter
    private final String commandEvents;

    public CommandAuditData(DateTime executionTime, VersionedId certificateAuthorityVersionedId, String principal, String commandType, CertificateAuthorityCommandGroup commandGroup, String commandSummary, String commandEvents) {
        super(executionTime, principal, commandSummary);
        this.certificateAuthorityVersionedId = certificateAuthorityVersionedId;
        this.commandType = commandType;
        this.commandGroup = commandGroup.name();
        this.commandEvents = commandEvents;
    }

    public long getCertificateAuthorityId() {
        return certificateAuthorityVersionedId.getId();
    }

    public long getCertificateAuthorityVersion() {
        return certificateAuthorityVersionedId.getVersion();
    }

    public CertificateAuthorityCommandGroup getCommandGroup() {
        return CertificateAuthorityCommandGroup.valueOf(commandGroup);
    }

    @Override
    public String getSummary() {
        return super.getSummary() + (StringUtils.isBlank(commandEvents) ? "" : ("\n" + commandEvents));
    }
}
