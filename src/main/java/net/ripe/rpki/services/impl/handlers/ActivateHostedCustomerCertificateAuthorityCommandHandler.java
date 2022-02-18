package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
// TODO(yg) rename to ActivateCustomerCertificateAuthorityCommandHandler
public class ActivateHostedCustomerCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateCustomerCertificateAuthorityCommand> {

    private final CertificationConfiguration certificationConfiguration;
    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    ActivateHostedCustomerCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                             CertificationConfiguration certificationConfiguration,
                                                             ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.certificationConfiguration = certificationConfiguration;
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<ActivateCustomerCertificateAuthorityCommand> commandType() {
        return ActivateCustomerCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(ActivateCustomerCertificateAuthorityCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);
        HostedCertificateAuthority productionCa = lookupHostedCA(command.getParentId());
        CustomerCertificateAuthority memberCa = createMemberCA(command, productionCa);

        childParentCertificateUpdateSaga.execute(productionCa, memberCa, Integer.MAX_VALUE);
    }

    private CustomerCertificateAuthority createMemberCA(ActivateCustomerCertificateAuthorityCommand command, HostedCertificateAuthority parentCa) {
        CustomerCertificateAuthority ca = new CustomerCertificateAuthority(command.getCertificateAuthorityVersionedId().getId(),
                command.getName(), parentCa, certificationConfiguration.getMaxSerialIncrement());
        getCertificateAuthorityRepository().add(ca);
        return ca;
    }

}
