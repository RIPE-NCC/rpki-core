package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

public class RenewMyIdentityMaterialCommand extends CertificateAuthorityModificationCommand {

    public RenewMyIdentityMaterialCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Renew Provisioning Identity Certificate for the delegation CA.";
    }
}
