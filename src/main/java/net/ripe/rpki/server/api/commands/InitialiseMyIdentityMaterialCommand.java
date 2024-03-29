package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;

/**
 * <p>
 * Create the {@link ProvisioningIdentityCertificate} for the {@link net.ripe.rpki.domain.ProductionCertificateAuthority production CA}.
 * </p> 
 */
public class InitialiseMyIdentityMaterialCommand extends CertificateAuthorityModificationCommand {

    public InitialiseMyIdentityMaterialCommand(VersionedId certificateAuthorityId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
    }

    @Override
    public String getCommandSummary() {
        return "Created Provisioning Identity Certificate for the delegation CA.";
    }
}
