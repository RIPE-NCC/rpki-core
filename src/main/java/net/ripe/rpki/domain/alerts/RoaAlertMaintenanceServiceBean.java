package net.ripe.rpki.domain.alerts;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service("roaAlertMaintenanceServiceBean")
public class RoaAlertMaintenanceServiceBean implements RoaAlertMaintenanceService {
    @Autowired
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Autowired
    private final CertificateAuthorityRepository certificateAuthorityRepository;

    @Autowired
    private final CommandAuditService commandAuditService;

    /**
     * Update the ROA alert subscriptions to match the resources on the new certificate.
     * Subscriptions can ignore an alert for a prefix that has overlap (equal, less, more specific) than something on
     * the certificate.
     */
    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        final VersionedId caId = event.getCertificateAuthorityVersionedId();
        final IpResourceSet nowCurrentResources = event.getIncomingCertificate().getResources();

        final RoaAlertConfiguration roaAlertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(caId.getId());
        if (roaAlertConfiguration == null) {
            return; // no configuration
        }
        // Update subscriptions, removing subscriptions for resources that do not overlap with the current resources.
        final List<AnnouncedRoute> alertsToRemove = roaAlertConfiguration.getIgnored().stream()
            .map(RoaAlertIgnoredAnnouncement::toData)
            .filter(ignoredAnnouncement -> {
                IpResourceSet prefix = new IpResourceSet(ignoredAnnouncement.getPrefix());

                return !(nowCurrentResources.contains(prefix) || prefix.contains(nowCurrentResources));
            })
            .collect(Collectors.toList());

        if (!alertsToRemove.isEmpty()) {
            roaAlertConfiguration.update(Collections.emptyList(), alertsToRemove);
            context.recordEvent(new RoaAlertIgnoredAnnouncedRoutesUpdatedEvent(caId, alertsToRemove));
        }
    }

    @Value
    public static class RoaAlertIgnoredAnnouncedRoutesUpdatedEvent {
        VersionedId caId;
        List<AnnouncedRoute> removedAlerts;

        @Override
        public String toString() {
            // This string representation is stored in the command audit table and shown to the user
            return String.format(
                "Updated suppressed routes for ROA alerts due to changed resources, deletions: %s.",
                String.join("; ", UpdateRoaAlertIgnoredAnnouncedRoutesCommand.getHumanReadableAnnouncedRoutes(removedAlerts))
            );
        }
    }
}
