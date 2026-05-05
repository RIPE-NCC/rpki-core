package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.RenewMyIdentityMaterialCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.NotImplementedException;


@Handler
public class RenewMyIdentityMaterialCommandHandler extends AbstractCertificateAuthorityCommandHandler<RenewMyIdentityMaterialCommand> {

    @Inject
    public RenewMyIdentityMaterialCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<RenewMyIdentityMaterialCommand> commandType() {
        return RenewMyIdentityMaterialCommand.class;
    }

    @Override
    public void handle(RenewMyIdentityMaterialCommand command, CommandStatus commandStatus) {

        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());

        if (CertificateAuthorityType.ROOT == ca.getType()) {
            ProductionCertificateAuthority productionCertificateAuthority = (ProductionCertificateAuthority) ca;
            productionCertificateAuthority.getMyDownStreamProvisioningCommunicator().renewProvisioningIdentityMaterial();
        } else {
            throw new NotImplementedException("Only implemented for ProductionCertificateAuthority");
        }
    }
}
