package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Slf4j
@Handler
public class DeleteCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<DeleteCertificateAuthorityCommand> {

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final RoaConfigurationRepository roaConfigurationRepository;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    private final KeyPairDeletionService keyPairDeletionService;
    private final CommandAuditService commandAuditService;

    @Inject
    public DeleteCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    ResourceCertificateRepository resourceCertificateRepository,
                                                    RoaConfigurationRepository roaConfigurationRepository,
                                                    RoaAlertConfigurationRepository roaAlertConfigurationRepository,
                                                    KeyPairDeletionService keyPairDeletionService,
                                                    CommandAuditService commandAuditService) {
        super(certificateAuthorityRepository);
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.commandAuditService = commandAuditService;
    }

    @Override
    public Class<DeleteCertificateAuthorityCommand> commandType() {
        return DeleteCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(DeleteCertificateAuthorityCommand command, CommandStatus commandStatus) {
        final CertificateAuthority ca = lookupCA(command.getCertificateAuthorityId());

        log.warn("deleting CA '{}' (id = {}, UUID = {})", ca.getName(), ca.getId(), ca.getUuid());

        ca.getSignedPublicKeys().forEach(publicKey -> {
            CertificateRevocationRequest request = new CertificateRevocationRequest(publicKey);
            CertificateRevocationResponse response = ca.getParent().processCertificateRevocationRequest(request, resourceCertificateRepository);
            ca.processCertificateRevocationResponse(response, keyPairDeletionService);
        });

        ca.asManagedCertificateAuthority().ifPresent(managedCa -> {
            // Remove the ROA (alert) configuration after deleting keys above, otherwise there will be no audit events
            // generated for the ROA (alert) configuration changes.
            roaConfigurationRepository.findByCertificateAuthority(managedCa)
                .ifPresent(roaConfigurationRepository::remove);
            RoaAlertConfiguration roaAlertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(managedCa.getId());
            if (roaAlertConfiguration != null) {
                roaAlertConfigurationRepository.remove(roaAlertConfiguration);
            }
        });

        commandAuditService.deleteCommandsForCa(ca.getId());
        getCertificateAuthorityRepository().remove(ca);
    }

}
