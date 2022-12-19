package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
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
        ManagedCertificateAuthority productionCa = lookupManagedCa(command.getParentId());

        NonHostedCertificateAuthority nonHosted = new NonHostedCertificateAuthority(
            command.getCertificateAuthorityId(), command.getName(),
            command.getUuid(), identityCertificate, productionCa);
        getCertificateAuthorityRepository().add(nonHosted);
    }
}
