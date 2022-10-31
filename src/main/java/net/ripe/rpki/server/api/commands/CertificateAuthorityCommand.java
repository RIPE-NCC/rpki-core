package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Base class for <b>all</b> commands related to Certificate Authorities.
 */
public abstract class CertificateAuthorityCommand {

    private final VersionedId certificateAuthorityId; // Maybe change name to certificationAuthorityVersionedId? If so, need to migrate existing serialised history

    private final CertificateAuthorityCommandGroup commandGroup;

    public CertificateAuthorityCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup) {
        Validate.notNull(certificateAuthorityId, "certificateAuthorityId is required");
        this.certificateAuthorityId = certificateAuthorityId;
        this.commandGroup = commandGroup;
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return certificateAuthorityId;
    }

    public long getCertificateAuthorityId() {
        return getCertificateAuthorityVersionedId().getId();
    }

    public CertificateAuthorityCommandGroup getCommandGroup() {
    	return commandGroup;
    }

    public String getCommandType() {
        return getClass().getSimpleName();
    }

    public abstract String getCommandSummary();

    /**
     * Reflection based to ensure consistency for all commands.
     */
    @Override
    public final boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    /**
     * Reflection based to ensure consistency for all commands.
     */
    @Override
    public final int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Reflection based to ensure consistency for all commands.
     */
    @Override
    public final String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
