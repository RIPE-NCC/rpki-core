package net.ripe.rpki.services.impl.handlers;


import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaEntityService;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;


@Handler
public class IssueUpdatedManifestAndCrlCommandHandler extends AbstractCertificateAuthorityCommandHandler<IssueUpdatedManifestAndCrlCommand> {

    private final ManifestPublicationService manifestPublicationService;
    private final AspaEntityService aspaEntityService;
    private final RoaEntityService roaEntityService;


    @Inject
    public IssueUpdatedManifestAndCrlCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    ManifestPublicationService manifestPublicationService,
                                                    AspaEntityService aspaEntityService,
                                                    RoaEntityService roaEntityService) {
        super(certificateAuthorityRepository);
        this.manifestPublicationService = manifestPublicationService;
        this.aspaEntityService = aspaEntityService;
        this.roaEntityService = roaEntityService;
    }

    @Override
    public Class<IssueUpdatedManifestAndCrlCommand> commandType() {
        return IssueUpdatedManifestAndCrlCommand.class;
    }

    @Override
    public void handle(IssueUpdatedManifestAndCrlCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority certificateAuthority = lookupManagedCa(command.getCertificateAuthorityId());

        boolean configurationCheckNeeded = certificateAuthority.isConfigurationCheckNeeded();
        if (configurationCheckNeeded) {
            aspaEntityService.updateAspaIfNeeded(certificateAuthority);
            roaEntityService.updateRoasIfNeeded(certificateAuthority);
            certificateAuthority.markConfigurationApplied();
        }

        long updateCount = manifestPublicationService.updateManifestAndCrlIfNeeded(certificateAuthority);
        if (!configurationCheckNeeded && updateCount == 0) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
