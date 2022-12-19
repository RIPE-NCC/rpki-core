package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Base class for <b>all</b> commands related to Certificate Authorities.
 */
@Getter
public abstract class CertificateAuthorityCommand {

    @NonNull
    private final VersionedId certificateAuthorityVersionedId;

    @NonNull
    private final CertificateAuthorityCommandGroup commandGroup;

    protected CertificateAuthorityCommand(VersionedId certificateAuthorityVersionedId, CertificateAuthorityCommandGroup commandGroup) {
        this.certificateAuthorityVersionedId = certificateAuthorityVersionedId;
        this.commandGroup = commandGroup;
    }

    public long getCertificateAuthorityId() {
        return getCertificateAuthorityVersionedId().getId();
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
