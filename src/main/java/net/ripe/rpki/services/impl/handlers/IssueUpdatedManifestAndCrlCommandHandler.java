package net.ripe.rpki.services.impl.handlers;


import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;


@Handler
public class IssueUpdatedManifestAndCrlCommandHandler extends AbstractCertificateAuthorityCommandHandler<IssueUpdatedManifestAndCrlCommand> {

    private final CertificateManagementService certificateManagementService;


    @Inject
    public IssueUpdatedManifestAndCrlCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    CertificateManagementService certificateManagementService) {
        super(certificateAuthorityRepository);
        this.certificateManagementService = certificateManagementService;
    }

    @Override
    public Class<IssueUpdatedManifestAndCrlCommand> commandType() {
        return IssueUpdatedManifestAndCrlCommand.class;
    }

    @Override
    public void handle(IssueUpdatedManifestAndCrlCommand command, CommandStatus commandStatus) {
        HostedCertificateAuthority hostedCa = lookupHostedCA(command.getCertificateAuthorityVersionedId().getId());
        long updateCount = certificateManagementService.updateManifestAndCrlIfNeeded(hostedCa);
        if (hostedCa.isManifestAndCrlCheckNeeded()) {
            hostedCa.manifestAndCrlCheckCompleted();
        } else if (updateCount == 0) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
