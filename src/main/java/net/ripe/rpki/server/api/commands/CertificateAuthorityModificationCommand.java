package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;


/**
 * Base class for commands that modify an existing certificate authority.
 */
public abstract class CertificateAuthorityModificationCommand extends CertificateAuthorityCommand {

    private static final long serialVersionUID = 1L;

    public CertificateAuthorityModificationCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup) {
        super(certificateAuthorityId, commandGroup);
    }
}
