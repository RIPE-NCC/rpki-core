package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Create a {@link RepublishRequest} for the offline TA.
 * Use the Delegation CA for the certificateAuthorityId, even though it's not affected.
 * </p>
 */
public class GenerateOfflineCARepublishRequestCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    public GenerateOfflineCARepublishRequestCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Generated Offline CA Republish Request.";
    }
}
