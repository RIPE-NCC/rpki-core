package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Issue an updated manifest and CRL if needed (mainly due to nearing the next update time of current manifest and CRL)
 * </p>
 */
public class IssueUpdatedManifestAndCrlCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    public IssueUpdatedManifestAndCrlCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
    }

    @Override
    public String getCommandSummary() {
        return "Issue updated manifest and CRL.";
    }
}
