package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Let the back-end update the <b>incoming</b> certificates for the CA.
 * </p><p>
 * NOTE: This command is used by the back-end in a background service. There should be no need to use this directly.
 * </p>
 */
public class UpdateAllIncomingResourceCertificatesCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    public UpdateAllIncomingResourceCertificatesCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
    }

    @Override
    public String getCommandSummary() {
        return "Updated all incoming certificates.";
    }
}
