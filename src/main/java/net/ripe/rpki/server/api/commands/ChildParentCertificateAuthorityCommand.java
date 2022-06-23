package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

public abstract class ChildParentCertificateAuthorityCommand extends CertificateAuthorityModificationCommand {
    public ChildParentCertificateAuthorityCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup) {
        super(certificateAuthorityId, commandGroup);
    }
}
