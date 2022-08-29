package net.ripe.rpki.services.impl.handlers;


import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;


@Handler
public class IssueUpdatedManifestAndCrlCommandHandler extends AbstractCertificateAuthorityCommandHandler<IssueUpdatedManifestAndCrlCommand> {

    private final RoaEntityService roaEntityService;
    private final CertificateManagementService certificateManagementService;


    @Inject
    public IssueUpdatedManifestAndCrlCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    RoaEntityService roaEntityService,
                                                    CertificateManagementService certificateManagementService) {
        super(certificateAuthorityRepository);
        this.roaEntityService = roaEntityService;
        this.certificateManagementService = certificateManagementService;
    }

    @Override
    public Class<IssueUpdatedManifestAndCrlCommand> commandType() {
        return IssueUpdatedManifestAndCrlCommand.class;
    }

    @Override
    public void handle(IssueUpdatedManifestAndCrlCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority hostedCa = lookupManagedCa(command.getCertificateAuthorityVersionedId().getId());

        roaEntityService.updateRoasIfNeeded(hostedCa);

        long updateCount = certificateManagementService.updateManifestAndCrlIfNeeded(hostedCa);
        if (hostedCa.isManifestAndCrlCheckNeeded()) {
            hostedCa.manifestAndCrlCheckCompleted();
        } else if (updateCount == 0) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
