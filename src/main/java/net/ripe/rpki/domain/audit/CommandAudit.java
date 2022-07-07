package net.ripe.rpki.domain.audit;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.dto.CommandAuditData;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * Command executed by the system.
 */
@Entity
@Table(name = "commandaudit")
public class CommandAudit extends AbstractAuditRecord {

    @Getter
    @Column(name = "commandtype", nullable = false)
    private String commandType;

    @Column(name = "commandgroup", nullable = false)
    private String commandGroup;

    @Getter
    @Column(name = "commandsummary", nullable = false)
    private String commandSummary;

    @Getter
    @Column(name = "commandevents", nullable = false)
    private String commandEvents;

    @Getter
    @Setter
    @Column(name = "deleted_at", nullable = true)
    private Timestamp deletedAt;

    protected CommandAudit() {
    }

    // TODO: Change principal to CertificationUserId after migrating existing entries
    public CommandAudit(String principal, VersionedId caId, CertificateAuthorityCommand command, String commandEvents) {
        super(principal, caId);
        this.commandType = command.getCommandType();
        this.commandGroup = command.getCommandGroup().name();
        this.commandSummary = command.getCommandSummary();
        this.commandEvents = commandEvents;
    }

    public CertificateAuthorityCommandGroup getCommandGroup() {
        return CertificateAuthorityCommandGroup.valueOf(commandGroup);
    }

    public CommandAuditData toData() {
        return new CommandAuditData(
                getExecutionTime(),
                getCertificateAuthorityVersionedId(),
                getPrincipal(),
                getCommandType(),
                getCommandGroup(),
                getCommandSummary(),
                getCommandEvents()
        );
    }
}
