package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.util.VersionedId;

import java.security.PublicKey;

/**
 * <p>
 * Process a provision certificate issuance request.
 * </p>
 */
@Getter
public class ProvisioningCertificateRevocationCommand extends ChildSharedParentCertificateAuthorityCommand {

    @NonNull
    private final PublicKey publicKey;

    public ProvisioningCertificateRevocationCommand(VersionedId certificateAuthorityId, PublicKey publicKey) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.publicKey = publicKey;
    }

    @Override
    public String getCommandSummary() {
        return "Process a provisioning certificate revocation request.";
    }
}
