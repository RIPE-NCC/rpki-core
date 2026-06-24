package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.Objects;

/**
 * Delete the mentioned Certificate Authority by an admin.
 */
public class AdminDeleteCertificateAuthorityCommand extends ChildParentCertificateAuthorityCommand {
    private final X500Principal name;
    private final String user;

    public AdminDeleteCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name, String user) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.name = Objects.requireNonNull(name, "name is required");
        this.user = Objects.requireNonNull(user, "user is required");
    }

    @Override
    public String getCommandSummary() {
        return "Delete Certificate Authority '" + name + "' by '" + user + "'.";
    }
}
