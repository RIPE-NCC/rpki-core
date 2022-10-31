package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;

/**
 * <p>
 * Create a {@link UpStreamCARequestEntity} for the offline TA.
 * </p>
 */
public class GenerateOfflineCARepublishRequestCommand extends CertificateAuthorityModificationCommand {

    public GenerateOfflineCARepublishRequestCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Generated Offline CA Republish Request.";
    }
}
