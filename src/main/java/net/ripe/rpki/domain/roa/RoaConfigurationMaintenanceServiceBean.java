package net.ripe.rpki.domain.roa;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service("roaConfigurationMaintenanceServiceBean")
public class RoaConfigurationMaintenanceServiceBean implements RoaConfigurationMaintenanceService {
    @Autowired
    private final RoaConfigurationRepository roaConfigurationRepository;

    @Autowired
    private final CertificateAuthorityRepository certificateAuthorityRepository;

    @Autowired
    private final CommandAuditService commandAuditService;

    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        final VersionedId caId = event.getCertificateAuthorityVersionedId();
        final IpResourceSet nowCurrentResources = event.getIncomingCertificate().getResources();

        final HostedCertificateAuthority ca = certificateAuthorityRepository.findHostedCa(caId.getId());

        final Optional<RoaConfiguration> maybeConfig = roaConfigurationRepository.findByCertificateAuthority(ca);
        if (!maybeConfig.isPresent()) {
            return;
        }

        final RoaConfiguration config = maybeConfig.get();
        // Filter out the prefixes not contained by the certificate.
        final Set<RoaConfigurationPrefix> toBeRemoved = config.getPrefixes().stream()
                .filter(prefix -> !nowCurrentResources.contains(prefix.getPrefix()))
                .collect(Collectors.toSet());

        if (!toBeRemoved.isEmpty()) {
            // Update the config, log the removed prefixes [...]
            config.removePrefix(toBeRemoved);
            roaConfigurationRepository.logRoaPrefixDeletion(config, toBeRemoved);

            context.recordEvent(
                    new RoaConfigurationUpdatedDueToChangedResourcesEvent(
                            caId,
                            toBeRemoved.stream()
                                    .map(RoaConfigurationPrefix::toData)
                                    .collect(Collectors.toSet()))
            );
        }
    }

    @Value
    public static class RoaConfigurationUpdatedDueToChangedResourcesEvent {
        VersionedId caId;
        Set<RoaConfigurationPrefixData> removedPrefixes;

        @Override
        public String toString() {
            // This string representation is stored in the command audit table and shown to the user
            return String.format(
                "Updated ROA configuration due to changed resources, removed prefixes: %s.",
                String.join("; ", UpdateRoaConfigurationCommand.getHumanReadableRoaPrefixData(removedPrefixes))
            );
        }
    }
}
