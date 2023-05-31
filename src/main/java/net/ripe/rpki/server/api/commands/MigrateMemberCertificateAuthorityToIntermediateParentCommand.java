package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

/**
 * Migrates an existing member certificate authority from the production CA to an intermediate CA.
 *
 * <p>
 * The current parent must be the production CA and new parent must be an intermediate CA. The new parent's parent must be
 * the same the member CA's parent.
 * </p>
 */
public class MigrateMemberCertificateAuthorityToIntermediateParentCommand extends CertificateAuthorityModificationCommand {


    @Getter
    private final long newParentId;

    public MigrateMemberCertificateAuthorityToIntermediateParentCommand(VersionedId certificateAuthorityId, long newParentId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
        this.newParentId = newParentId;
    }

    @Override
    public String getCommandSummary() {
        return "Migrate member CA to intermediate CA parent.";
    }
}
