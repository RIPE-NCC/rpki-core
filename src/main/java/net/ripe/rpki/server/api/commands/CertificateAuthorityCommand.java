package net.ripe.rpki.server.api.commands;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ripe.rpki.commons.util.EqualsSupport;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

import java.io.Serializable;

/**
 * Base class for <b>all</b> commands related to Certificate Authorities.
 */
@ToString
@EqualsAndHashCode
public abstract class CertificateAuthorityCommand implements Serializable {

    private static final long serialVersionUID = 1L;

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
}
