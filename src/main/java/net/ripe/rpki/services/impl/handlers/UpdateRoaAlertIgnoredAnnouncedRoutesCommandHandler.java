package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;


@Handler
public class UpdateRoaAlertIgnoredAnnouncedRoutesCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateRoaAlertIgnoredAnnouncedRoutesCommand> {

    private final RoaAlertConfigurationRepository repository;

    @Inject
    public UpdateRoaAlertIgnoredAnnouncedRoutesCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository, RoaAlertConfigurationRepository repository) {
        super(certificateAuthorityRepository);
        this.repository = repository;
    }

    @Override
    public Class<UpdateRoaAlertIgnoredAnnouncedRoutesCommand> commandType() {
        return UpdateRoaAlertIgnoredAnnouncedRoutesCommand.class;
    }

    @Override
    public void handle(UpdateRoaAlertIgnoredAnnouncedRoutesCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());
        RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(command.getCertificateAuthorityId());
        if (configuration == null) {
            configuration = new RoaAlertConfiguration(ca);
            configuration.clearSubscription();
            repository.add(configuration);
        }
        configuration.update(command.getAdditions(), command.getDeletions());
    }
}
