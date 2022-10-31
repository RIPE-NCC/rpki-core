package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Delete the mentioned Non-hosted Certificate Authority. Use with extreme prejudice...
 * </p>
 */
public class DeleteNonHostedCertificateAuthorityCommand extends ChildParentCertificateAuthorityCommand {

    public DeleteNonHostedCertificateAuthorityCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Deleted Non-hosted Certificate Authority.";
    }
}
