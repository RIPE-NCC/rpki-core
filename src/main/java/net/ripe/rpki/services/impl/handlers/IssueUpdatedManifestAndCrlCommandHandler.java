package net.ripe.rpki.services.impl.handlers;


import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaEntityService;
import net.ripe.rpki.domain.bgpsec.BgpSecEntityService;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import jakarta.inject.Inject;


@Handler
public class IssueUpdatedManifestAndCrlCommandHandler extends AbstractCertificateAuthorityCommandHandler<IssueUpdatedManifestAndCrlCommand> {

    private final ManifestPublicationService manifestPublicationService;
    private final AspaEntityService aspaEntityService;
    private final RoaEntityService roaEntityService;
    private final BgpSecEntityService bgpSecEntityService;

    @Inject
    public IssueUpdatedManifestAndCrlCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    ManifestPublicationService manifestPublicationService,
                                                    AspaEntityService aspaEntityService,
                                                    RoaEntityService roaEntityService,
                                                    BgpSecEntityService bgpSecEntityService) {
        super(certificateAuthorityRepository);
        this.manifestPublicationService = manifestPublicationService;
        this.aspaEntityService = aspaEntityService;
        this.roaEntityService = roaEntityService;
        this.bgpSecEntityService = bgpSecEntityService;
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
            roaEntityService.updateRoasIfNeeded(certificateAuthority);
            aspaEntityService.updateAspaIfNeeded(certificateAuthority);
            bgpSecEntityService.updateBgpSecIfNeeded(certificateAuthority);
            certificateAuthority.markConfigurationApplied();
        }

        long publishedCount = manifestPublicationService.publishRpkiObjectsIfNeeded(certificateAuthority);
        if (!configurationCheckNeeded && publishedCount == 0) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
