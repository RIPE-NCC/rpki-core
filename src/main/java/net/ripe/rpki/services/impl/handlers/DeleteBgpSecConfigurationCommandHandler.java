package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.bgpsec.BgpSecConfigurationRepository;
import net.ripe.rpki.server.api.commands.DeleteBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

@Handler
public class DeleteBgpSecConfigurationCommandHandler extends AbstractCertificateAuthorityCommandHandler<DeleteBgpSecConfigurationCommand> {

    private final BgpSecConfigurationRepository bgpSecConfigurationRepository;

    @Inject
    public DeleteBgpSecConfigurationCommandHandler(
            CertificateAuthorityRepository certificateAuthorityRepository,
            BgpSecConfigurationRepository bgpSecConfigurationRepository) {
        super(certificateAuthorityRepository);
        this.bgpSecConfigurationRepository = bgpSecConfigurationRepository;
    }

    @Override
    public Class<DeleteBgpSecConfigurationCommand> commandType() {
        return DeleteBgpSecConfigurationCommand.class;
    }

    @Override
    public void handle(@NonNull DeleteBgpSecConfigurationCommand command, CommandStatus commandStatus) {
        var ca = lookupManagedCa(command.getCertificateAuthorityId());
        var exists = bgpSecConfigurationRepository.findByCertificateAuthorityAndId(ca, command.getRemoved().id()).isPresent();
        if (!exists) {
            throw new CommandWithoutEffectException(command);
        }

        bgpSecConfigurationRepository.removeById(ca, command.getRemoved().id());
        ca.markConfigurationUpdated();
    }

}
