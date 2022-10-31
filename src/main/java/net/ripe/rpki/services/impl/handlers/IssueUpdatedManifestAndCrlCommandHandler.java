package net.ripe.rpki.services.impl.handlers;


import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;


@Handler
public class IssueUpdatedManifestAndCrlCommandHandler extends AbstractCertificateAuthorityCommandHandler<IssueUpdatedManifestAndCrlCommand> {

    private final ManifestPublicationService manifestPublicationService;


    @Inject
    public IssueUpdatedManifestAndCrlCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    ManifestPublicationService manifestPublicationService) {
        super(certificateAuthorityRepository);
        this.manifestPublicationService = manifestPublicationService;
    }

    @Override
    public Class<IssueUpdatedManifestAndCrlCommand> commandType() {
        return IssueUpdatedManifestAndCrlCommand.class;
    }

    @Override
    public void handle(IssueUpdatedManifestAndCrlCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority hostedCa = lookupManagedCa(command.getCertificateAuthorityVersionedId().getId());

        long updateCount = manifestPublicationService.updateManifestAndCrlIfNeeded(hostedCa);
        if (hostedCa.isManifestAndCrlCheckNeeded()) {
            hostedCa.manifestAndCrlCheckCompleted();
        } else if (updateCount == 0) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
