package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang3.Validate;

/**
 * <p>
 * Let the back-end update the <b>incoming</b> certificates for the CA.
 * </p><p>
 * NOTE: This command is used by the back-end in a background service. There should be no need to use this directly.
 * </p>
 */
public class UpdateAllIncomingResourceCertificatesCommand extends ChildParentCertificateAuthorityCommand {

    private static final long serialVersionUID = 1L;

    @Getter
    private final int issuedCertificatesPerSignedKeyLimit;

    public UpdateAllIncomingResourceCertificatesCommand(VersionedId certificateAuthorityId, int issuedCertificatesPerSignedKeyLimit) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
        Validate.isTrue(issuedCertificatesPerSignedKeyLimit > 0, "issuedCertificatesPerSignedKeyLimit must be positive");
        this.issuedCertificatesPerSignedKeyLimit = issuedCertificatesPerSignedKeyLimit;
    }

    @Override
    public String getCommandSummary() {
        return "Updated all incoming certificates.";
    }
}
