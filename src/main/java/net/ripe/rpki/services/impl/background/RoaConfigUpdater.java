package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository.RoaConfigurationPerCa;
import net.ripe.rpki.server.api.commands.CertificateAuthorityModificationCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.mapList;

@Slf4j
@Service
public class RoaConfigUpdater {
    private final CertificateAuthorityViewService certificateService;
    private final RoaConfigurationRepository roaConfigurationRepository;
    private final CommandService commandService;

    @Autowired
    public RoaConfigUpdater(CertificateAuthorityViewService certificateService,
                            RoaConfigurationRepository roaConfigurationRepository,
                            CommandService commandService) {
        this.certificateService = certificateService;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.commandService = commandService;
    }

    void updateRoaConfig(final Map<CaName, IpResourceSet> resourcesPerCA) {

        final Predicate<RoaConfigurationPerCa> belongsToLostResource = config -> {
            final IpResourceSet resourcesForCA = resourcesPerCA.get(config.caName);
            return resourcesForCA == null || !resourcesForCA.contains(config.prefix);
        };
        final List<RoaConfigurationPerCa> configsToDelete = roaConfigurationRepository.findAllPerCa()
                .stream()
                .filter(belongsToLostResource)
                .collect(Collectors.toList());

        final List<CertificateAuthorityModificationCommand> commands = generateUpdateCommands(configsToDelete);

        if (commands.size() > 0) {
            final String updateRoaConfigCommandsSummary = commands.stream()
                    .map(CertificateAuthorityModificationCommand::getCommandSummary)
                    .collect(Collectors.joining("\n  "));

            log.info("About to execute Update ROA Configuration commands:\n {}", updateRoaConfigCommandsSummary);

            commands.forEach(commandService::execute);
        }
    }

    private List<CertificateAuthorityModificationCommand> generateUpdateCommands(List<RoaConfigurationPerCa> configsToDelete) {
        return configsToDelete.stream()
                .collect(Collectors.groupingBy(r -> r.caId))
                .entrySet().stream()
                .flatMap(entry -> {
                    final VersionedId caVersionedId = certificateService.findCertificateAuthority(entry.getKey()).getVersionedId();

                    final List<RoaConfigurationPrefixData> deletedPrefixData = mapList(entry.getValue(),
                            r -> new RoaConfigurationPrefixData(r.asn, IpRange.range((IpAddress) r.prefix.getStart(), (IpAddress) r.prefix.getEnd()), r.maximumLength));

                    return Stream.of(
                            new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(caVersionedId, Collections.emptyList(), mapList(deletedPrefixData, r -> new AnnouncedRoute(r.getAsn(), r.getPrefix()))),
                            new UpdateRoaConfigurationCommand(caVersionedId, Collections.emptyList(), deletedPrefixData)
                    );
                })
                .collect(Collectors.toList());
    }


}
