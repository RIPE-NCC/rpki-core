package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * Instruct the CA to revoke old keys
 * See step 6 of: http://tools.ietf.org/html/rfc6489#section-2
 */
public class KeyManagementRevokeOldKeysCommand extends ChildSharedParentCertificateAuthorityCommand {

    private static final long serialVersionUID = 1L;

    public KeyManagementRevokeOldKeysCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
    }

    @Override
    public String getCommandSummary() {
        return "Revoked old keys.";
    }
}
