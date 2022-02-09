package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

public class AllResourcesCaResourcesCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    public AllResourcesCaResourcesCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Updated Certificate Authority resources";
    }
}
