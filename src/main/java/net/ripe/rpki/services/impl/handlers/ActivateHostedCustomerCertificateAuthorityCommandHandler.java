package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
// TODO(yg) rename to ActivateCustomerCertificateAuthorityCommandHandler
public class ActivateHostedCustomerCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateCustomerCertificateAuthorityCommand> {

    private final KeyPairService keyPairService;
    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    ActivateHostedCustomerCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                             KeyPairService keyPairService,
                                                             ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<ActivateCustomerCertificateAuthorityCommand> commandType() {
        return ActivateCustomerCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull ActivateCustomerCertificateAuthorityCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority productionCa = lookupManagedCa(command.getParentId());
        CustomerCertificateAuthority memberCa = createMemberCA(command, productionCa);
        memberCa.createNewKeyPair(keyPairService);
        childParentCertificateUpdateSaga.execute(memberCa, Integer.MAX_VALUE);
    }

    private CustomerCertificateAuthority createMemberCA(ActivateCustomerCertificateAuthorityCommand command, ManagedCertificateAuthority parentCa) {
        CustomerCertificateAuthority ca = new CustomerCertificateAuthority(command.getCertificateAuthorityVersionedId().getId(),
                command.getName(), parentCa);
        getCertificateAuthorityRepository().add(ca);
        return ca;
    }

}
