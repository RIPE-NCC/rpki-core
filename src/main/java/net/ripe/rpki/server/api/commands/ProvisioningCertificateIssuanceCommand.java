package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.RequestedResourceSets;

import java.security.PublicKey;
import java.util.List;

/**
 * <p>
 * Process a provision certificate issuance request.
 * </p>
 */
@Getter
public class ProvisioningCertificateIssuanceCommand extends ChildSharedParentCertificateAuthorityCommand {

    private static final long serialVersionUID = 1L;

    @NonNull private final PublicKey publicKey;
    @NonNull private final RequestedResourceSets requestedResourceSets;
    @NonNull private final List<X509CertificateInformationAccessDescriptor> sia;

    public ProvisioningCertificateIssuanceCommand(VersionedId certificateAuthorityId, PublicKey publicKey, RequestedResourceSets requestedResourceSets, List<X509CertificateInformationAccessDescriptor> sia) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.publicKey = publicKey;
        this.requestedResourceSets = requestedResourceSets;
        this.sia = sia;
    }

    @Override
    public String getCommandSummary() {
        return "Process a provisioning certificate issuance request.";
    }
}
