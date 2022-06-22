package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
public class ActivateNonHostedCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateNonHostedCertificateAuthorityCommand> {

    @Inject
    public ActivateNonHostedCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<ActivateNonHostedCertificateAuthorityCommand> commandType() {
        return ActivateNonHostedCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(ActivateNonHostedCertificateAuthorityCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);

        ProvisioningIdentityCertificate identityCertificate = command.getIdentityCertificate();
        HostedCertificateAuthority productionCa = lookupHostedCA(command.getParentId());

        NonHostedCertificateAuthority nonHosted = new NonHostedCertificateAuthority(command.getCertificateAuthorityVersionedId().getId(), command.getName(), identityCertificate, productionCa);
        getCertificateAuthorityRepository().add(nonHosted);
    }
}
