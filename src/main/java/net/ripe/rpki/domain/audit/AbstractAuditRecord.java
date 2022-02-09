package net.ripe.rpki.domain.audit;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.SequenceGenerator;
import java.sql.Timestamp;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@SequenceGenerator(name = "seq_audit_record", sequenceName = "seq_all", allocationSize = 1)
public abstract class AbstractAuditRecord implements net.ripe.rpki.ncc.core.domain.support.Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_audit_record")
    private Long id;

    @Column(name = "executiontime", nullable = false)
    @Getter
    private DateTime executionTime;

    @Column(name = "ca_id", nullable = false)
    private long certificateAuthorityId;

    @Column(name = "ca_version", nullable = false)
    private long certificateAuthorityVersion;

    @Column(nullable = false)
    private String principal;

    protected AbstractAuditRecord() {}

    public AbstractAuditRecord(String principal, VersionedId certificateAuthorityVersionedId) {
        this.executionTime = new DateTime(DateTimeZone.UTC);
        this.principal = principal;
        this.certificateAuthorityId = certificateAuthorityVersionedId.getId();
        this.certificateAuthorityVersion = certificateAuthorityVersionedId.getVersion();
    }

    public long getCertificateAuthorityId() {
        return certificateAuthorityId;
    }

    public long getCertificateAuthorityVersion() {
        return certificateAuthorityVersion;
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return new VersionedId(certificateAuthorityId, certificateAuthorityVersion);
    }

    public String getPrincipal() {
        return principal;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public Object getId() {
        return id;
    }
}
