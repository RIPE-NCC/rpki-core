package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Create the Production Certificate Authority.
 * </p>
 * <p>
 * <b>The system does not protect against creating more than one of these. Please, do NOT do this. We'll build checks in the back-end in the future to ensure this doesn't happen.. For now, just don't..</b>
 * </p>
 */
public class CreateRootCertificateAuthorityCommand extends CertificateAuthorityCreationCommand {

    private static final long serialVersionUID = 1L;

    public CreateRootCertificateAuthorityCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, new IpResourceSet());
    }

    @Override
    public String getCommandSummary() {
        return "Created Production Certificate Authority.";
    }
}
