package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

/**
 * <p>
 * Create the Production Certificate Authority.
 * </p>
 * <p>
 * <b>The system does not protect against creating more than one of these. Please, do NOT do this. We'll build checks in the back-end in the future to ensure this doesn't happen.. For now, just don't..</b>
 * </p>
 */
public class CreateRootCertificateAuthorityCommand extends CertificateAuthorityCreationCommand {

    public CreateRootCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name) {
        super(certificateAuthorityId, name, UUID.randomUUID(), ImmutableResourceSet.empty());
    }

    @Override
    public String getCommandSummary() {
        return String.format("Created Production Certificate Authority '%s'.", getName());
    }
}
