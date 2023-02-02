package net.ripe.rpki.rest.service;


import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.Subscriptions;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.server.api.commands.UnsubscribeFromRoaAlertCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/alerts", produces = {APPLICATION_JSON})
@Tag(name = "/ca/{caName}/alerts", description = "View of CA e-mail alerts")
public class AlertService extends AbstractCaRestService {
    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;

    private final CommandService commandService;

    @Autowired
    public AlertService(RoaAlertConfigurationViewService roaAlertConfigurationViewService, CommandService commandService) {
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
        this.commandService = commandService;
    }

    @Operation(summary = "Get all alerts belonging to a CA")
    @GetMapping
    public ResponseEntity<Subscriptions> getAlertsForCa(@PathVariable("caName") final CaName caName) {
        log.info("Getting alerts for CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final RoaAlertConfigurationData configuration = roaAlertConfigurationViewService.findRoaAlertSubscription(ca.getId());
        if (configuration == null) {
            return ok(new Subscriptions(Collections.emptySet(), Collections.emptySet()));
        }

        final Set<String> validityStates = configuration.getRouteValidityStates().stream()
            .map(RouteValidityState::name)
            .collect(Collectors.toSet());

        final RoaAlertFrequency frequency = configuration.getSubscription() == null ?
            RoaAlertFrequency.DAILY : configuration.getSubscription().getFrequency();

        return ok(new Subscriptions(Sets.newHashSet(configuration.getEmails()), validityStates, frequency));
    }

    @PostMapping(consumes = {APPLICATION_JSON})
    @Operation(summary = "Subscribe/Unsubscribe for alerts about invalid or unknown announcements")
    public ResponseEntity<?> subscribe(@PathVariable("caName") final CaName caName, @RequestBody final Subscriptions newSubscription) {
        log.info("Subscribing to alerts about invalid or unknown announcement caName[{}], subscription {}", caName, newSubscription);
        if (newSubscription == null) {
            return badRequest("No valid subscription provided");
        }

        final Set<RouteValidityState> newValidityStates = newSubscription.getRouteValidityStates().stream()
            .map(RouteValidityState::valueOf)
            .collect(Collectors.toSet());

        final Set<String> newEmails = newSubscription.getEmails();

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final RoaAlertConfigurationData currentConfiguration = roaAlertConfigurationViewService.findRoaAlertSubscription(ca.getId());
        final Set<String> currentEmails = currentConfiguration == null || currentConfiguration.getEmails() == null ?
            Collections.emptySet() : new HashSet<>(currentConfiguration.getEmails());
        final Set<RouteValidityState> currentValidityStates = currentConfiguration == null || currentConfiguration.getRouteValidityStates() == null ?
            Collections.emptySet() : currentConfiguration.getRouteValidityStates();
        final RoaAlertFrequency currentFrequency = currentConfiguration == null || currentConfiguration.getSubscription() == null ?
            null : currentConfiguration.getSubscription().getFrequency();

        if (newValidityStates.isEmpty()) {
            currentEmails.forEach(email ->
                commandService.execute(new UnsubscribeFromRoaAlertCommand(ca.getVersionedId(), email)));
        } else {
            // Unsubscribe addresses that are no longer in the new subscription list.
            currentEmails.stream()
                .filter(object -> !newEmails.contains(object))
                .forEach(email -> commandService.execute(new UnsubscribeFromRoaAlertCommand(ca.getVersionedId(), email)));

            // If both validity and frequency remains, only subscribe additional email.
            if (newValidityStates.equals(currentValidityStates) && newSubscription.getFrequency().equals(currentFrequency)) {
                newEmails.stream()
                    .filter(email -> !currentEmails.contains(email))
                    .forEach(email ->
                        commandService.execute(new SubscribeToRoaAlertCommand(ca.getVersionedId(), email, newValidityStates, newSubscription.getFrequency())));

            } else {
                // Either validity or frequency changes so these guys have to be subscribed.
                newEmails.forEach(email ->
                    commandService.execute(new SubscribeToRoaAlertCommand(ca.getVersionedId(), email, newValidityStates, newSubscription.getFrequency())));
            }
        }
        return ok();
    }

    @PostMapping(path = "/suppress", consumes = {APPLICATION_JSON})
    @Operation(summary = "Suppress alerts for announcements")
    public ResponseEntity<?> suppress(@PathVariable("caName") final CaName caName, @RequestBody final List<BgpAnnouncement> announcements) {
        log.info("Suppress alerts for announcements for CA: {}", caName);
        HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        return processMuteOrUnMute(ca, getAnnouncedRoutes(announcements), Collections.emptySet());
    }

    @PostMapping(path = "/unsuppress", consumes = {APPLICATION_JSON})
    @Operation(summary = "Enable alerts for announcements")
    public ResponseEntity<?> enable(@PathVariable("caName") final CaName caName, @RequestBody final List<BgpAnnouncement> announcements) {
        log.info("Enable alerts for announcements for CA: {}", caName);
        HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        return processMuteOrUnMute(ca, Collections.emptySet(), getAnnouncedRoutes(announcements));
    }

    private ResponseEntity<?> processMuteOrUnMute(final HostedCertificateAuthorityData ca, final Collection<AnnouncedRoute> toMute, final Collection<AnnouncedRoute> toUnmute) {
        commandService.execute(new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(ca.getVersionedId(), toMute, toUnmute));
        return created();
    }

    private Collection<AnnouncedRoute> getAnnouncedRoutes(List<BgpAnnouncement> announcements) {
        return announcements.stream()
            .map(bgp -> new AnnouncedRoute(Asn.parse(bgp.getAsn()), IpRange.parse(bgp.getPrefix())))
            .collect(Collectors.toList());
    }

}
