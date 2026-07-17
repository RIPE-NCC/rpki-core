package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import net.ripe.rpki.domain.bgpsec.BgpSecConfigurationRepository;
import net.ripe.rpki.domain.bgpsec.Csr;
import net.ripe.rpki.server.api.commands.CreateBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;

@Handler
public class AddBgpSecConfigurationCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateBgpSecConfigurationCommand> {

    private final BgpSecConfigurationRepository bgpSecConfigurationRepository;

    @Inject
    public AddBgpSecConfigurationCommandHandler(
            CertificateAuthorityRepository certificateAuthorityRepository,
            BgpSecConfigurationRepository bgpSecConfigurationRepository) {
        super(certificateAuthorityRepository);
        this.bgpSecConfigurationRepository = bgpSecConfigurationRepository;
    }

    @Override
    public Class<CreateBgpSecConfigurationCommand> commandType() {
        return CreateBgpSecConfigurationCommand.class;
    }

    @Override
    public void handle(@NonNull CreateBgpSecConfigurationCommand command, CommandStatus commandStatus) {
        var ca = lookupManagedCa(command.getCertificateAuthorityId());
        var currentConfiguration = bgpSecConfigurationRepository.findByCertificateAuthority(ca)
                .stream().map(BgpSecConfiguration::withId).toList();

        var exists = currentConfiguration.stream()
                .anyMatch(config ->config.routerId().equals(command.getRouterId()) &&
                    config.keyIdentifier().equalsIgnoreCase(command.getKeyIdentifier())
                        && config.asn().equals(command.getAsn())
                );

        if (exists) {
            throw new CommandWithoutEffectException(command);
        }
        if (!ca.getCertifiedResources().contains(command.getAsn())) {
            throw new NotHolderOfResourcesException(ImmutableResourceSet.of(command.getAsn()));
        }
        Csr.validate(command.getCsr());

        bgpSecConfigurationRepository.add(new BgpSecConfiguration(ca, command.getAsn(), command.getRouterId().value(), command.getCsr()));
        ca.markConfigurationUpdated();
    }

}
