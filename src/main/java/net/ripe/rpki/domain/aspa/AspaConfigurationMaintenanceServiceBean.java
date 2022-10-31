package net.ripe.rpki.domain.aspa;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.IncomingCertificateRevokedEvent;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Updates the ASPA configuration based on the current certified resources, removing any ASPA configuration with a that
 * customer ASN that is not part of the certified resource.
 */
@Slf4j
@Service
public class AspaConfigurationMaintenanceServiceBean implements CertificateAuthorityEventVisitor {
    private final AspaConfigurationRepository aspaConfigurationRepository;

    private final CertificateAuthorityRepository certificateAuthorityRepository;

    public AspaConfigurationMaintenanceServiceBean(
        AspaConfigurationRepository aspaConfigurationRepository,
        CertificateAuthorityRepository certificateAuthorityRepository
    ) {
        this.aspaConfigurationRepository = aspaConfigurationRepository;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
    }

    @Override
    public void visitIncomingCertificateRevokedEvent(IncomingCertificateRevokedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityVersionedId().getId());
        if (ca == null) {
            return;
        }

        updateAspaConfigurationForResources(ca, ca.getCertifiedResources(), context);
    }

    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityVersionedId().getId());
        IpResourceSet nowCurrentResources = event.getIncomingCertificate().getResources();

        updateAspaConfigurationForResources(ca, nowCurrentResources, context);
    }


    private void updateAspaConfigurationForResources(ManagedCertificateAuthority ca, IpResourceSet certifiedResources, CommandContext context) {
        SortedMap<Asn, AspaConfiguration> configuration = aspaConfigurationRepository.findByCertificateAuthority(ca);

        List<AspaConfiguration> toBeRemoved = configuration.values().stream()
            .filter(entry -> !certifiedResources.contains(entry.getCustomerAsn()))
            .collect(Collectors.toList());
        if (toBeRemoved.isEmpty()) {
            return;
        }

        toBeRemoved.forEach(aspaConfigurationRepository::remove);
        ca.configurationUpdated();

        context.recordEvent(
            new AspaConfigurationUpdatedDueToChangedResourcesEvent(
                ca.getVersionedId(),
                toBeRemoved.stream()
                    .map(AspaConfiguration::toData)
                    .collect(Collectors.toList()))
        );
    }

    @Value
    public static class AspaConfigurationUpdatedDueToChangedResourcesEvent {
        VersionedId caId;
        List<AspaConfigurationData> removed;

        @Override
        public String toString() {
            // This string representation is stored in the command audit table and shown to the user
            return String.format(
                "Updated ASPA configuration due to changed resources, removed customer ASNs: %s.",
                UpdateAspaConfigurationCommand.getHumanReadableAspaConfiguration(removed)
            );
        }
    }
}
