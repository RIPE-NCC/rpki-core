package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.InitialiseMyIdentityMaterialCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.NotImplementedException;

import javax.inject.Inject;


@Handler
public class InitialiseMyIdentityMaterialCommandHandler extends AbstractCertificateAuthorityCommandHandler<InitialiseMyIdentityMaterialCommand> {

    private final KeyPairService keyPairService;

    @Inject
    public InitialiseMyIdentityMaterialCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository, KeyPairService keyPairService) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
    }

    @Override
    public Class<InitialiseMyIdentityMaterialCommand> commandType() {
        return InitialiseMyIdentityMaterialCommand.class;
    }

    @Override
    public void handle(InitialiseMyIdentityMaterialCommand command, CommandStatus commandStatus) {

        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityVersionedId().getId());

        if(CertificateAuthorityType.ROOT == ca.getType()) {
            DownStreamProvisioningCommunicator identityMaterial = keyPairService.createMyIdentityMaterial(ca);
            ((ProductionCertificateAuthority)ca).setMyDownStreamProvisioningCommunicator(identityMaterial);

        } else {
            throw new NotImplementedException("Only implemented for ProductionCertificateAuthority");
        }
    }
}
