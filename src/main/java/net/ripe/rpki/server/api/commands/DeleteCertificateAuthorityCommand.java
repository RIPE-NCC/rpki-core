package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;

/**
 * <p>
 * Delete the mentioned Certificate Authority. Use with extreme prejudice...
 * </p>
 */
public class DeleteCertificateAuthorityCommand extends ChildParentCertificateAuthorityCommand {
    private final X500Principal name;

    public DeleteCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.name = name;
    }

    @Override
    public String getCommandSummary() {
        return "Deleted Certificate Authority '" + name + "'";
    }
}
