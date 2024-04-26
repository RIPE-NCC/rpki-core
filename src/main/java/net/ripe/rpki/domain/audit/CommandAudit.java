package net.ripe.rpki.domain.audit;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import jakarta.persistence.*;
import javax.security.auth.x500.X500Principal;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Command executed by the system.
 */
@Entity
@SequenceGenerator(name = "seq_audit_record", sequenceName = "seq_all", allocationSize = 1)
@Table(name = "commandaudit")
public class CommandAudit implements net.ripe.rpki.ncc.core.domain.support.Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_audit_record")
    @Getter
    private Long id;
    @Column(name = "executiontime", nullable = false)
    @Getter
    private DateTime executionTime;
    @Column(name = "ca_id", nullable = false)
    @Getter
    private long certificateAuthorityId;
    @Column(name = "ca_version", nullable = false)
    @Getter
    private long certificateAuthorityVersion;
    @Column(name = "ca_name")
    @Getter
    private X500Principal caName;
    @Column(name = "ca_uuid")
    @Getter
    private UUID caUuid;

    @Column(nullable = false)
    @Getter
    private String principal;

    @Getter
    @Column(name = "commandtype", nullable = false)
    private String commandType;

    @Column(name = "commandgroup", nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter
    private CertificateAuthorityCommandGroup commandGroup;

    @Getter
    @Column(name = "commandsummary", nullable = false)
    private String commandSummary;

    @NonNull
    @Getter
    @Setter
    @Column(name = "commandevents", nullable = false)
    private String commandEvents = "";

    @Getter
    @Setter
    @Column(
        name = "deleted_at",
        // Managed with SQL statements, so no insert/updates from JPA allowed
        insertable = false, updatable = false
    )
    private Timestamp deletedAt;

    protected CommandAudit() {
    }

    // TODO: Change principal to CertificationUserId after migrating existing entries
    public CommandAudit(String principal, VersionedId caId, X500Principal caName, UUID caUuid, CertificateAuthorityCommand command) {
        this.caName = caName;
        this.caUuid = caUuid;
        this.executionTime = new DateTime(DateTimeZone.UTC);
        this.principal = principal;
        this.certificateAuthorityId = caId.getId();
        this.certificateAuthorityVersion = caId.getVersion();
        this.commandType = command.getCommandType();
        this.commandGroup = command.getCommandGroup();
        this.commandSummary = command.getCommandSummary();
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return new VersionedId(certificateAuthorityId, certificateAuthorityVersion);
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
