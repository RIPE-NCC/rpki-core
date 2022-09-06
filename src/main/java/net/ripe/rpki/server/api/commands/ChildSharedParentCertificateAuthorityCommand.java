package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

public abstract class ChildSharedParentCertificateAuthorityCommand extends CertificateAuthorityModificationCommand {
    public ChildSharedParentCertificateAuthorityCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup) {
        super(certificateAuthorityId, commandGroup);
    }
}
