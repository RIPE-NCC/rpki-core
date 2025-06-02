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
import net.ripe.rpki.server.api.commands.*;
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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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
            return ok(Subscriptions.defaultSubscriptions());
        }

        final Set<String> validityStates = configuration.getRouteValidityStates().stream()
                .map(RouteValidityState::name)
                .collect(Collectors.toSet());

        var subscription = configuration.getSubscription();
        if (subscription == null) {
            return ok(Subscriptions.defaultSubscriptions(configuration.getEmails(), validityStates));
        }
        return ok(new Subscriptions(Sets.newHashSet(configuration.getEmails()),
                validityStates,
                subscription.getFrequency(),
                subscription.isNotifyOnRoaChanges()));
    }

    @PostMapping(consumes = {APPLICATION_JSON})
    @Operation(summary = "Subscribe/Unsubscribe for alerts about invalid or unknown announcements")
    public ResponseEntity<?> subscribe(@PathVariable("caName") final CaName caName,
                                       @RequestBody final Subscriptions newSubscription) {
        log.info("Subscribing to alerts about invalid or unknown announcement caName[{}], subscription {}", caName, newSubscription);
        if (newSubscription == null) {
            return badRequest("No valid subscription provided");
        }
        if (newSubscription.getFrequency() == null) {
            return badRequest("No valid subscription frequency provided");
        }
        doSubscribe(caName, newSubscription);
        return ok();
    }

    @PostMapping(path = "/suppress", consumes = {APPLICATION_JSON})
    @Operation(summary = "Suppress alerts for announcements")
    public ResponseEntity<?> suppress(@PathVariable("caName") final CaName caName,
                                      @RequestBody final List<BgpAnnouncement> announcements) {
        log.info("Suppress alerts for announcements for CA: {}", caName);
        HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        return processMuteOrUnMute(ca, getAnnouncedRoutes(announcements), Collections.emptySet());
    }

    @PostMapping(path = "/unsuppress", consumes = {APPLICATION_JSON})
    @Operation(summary = "Enable alerts for announcements")
    public ResponseEntity<?> enable(@PathVariable("caName") final CaName caName,
                                    @RequestBody final List<BgpAnnouncement> announcements) {
        log.info("Enable alerts for announcements for CA: {}", caName);
        HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        return processMuteOrUnMute(ca, Collections.emptySet(), getAnnouncedRoutes(announcements));
    }

    private ResponseEntity<?> processMuteOrUnMute(final HostedCertificateAuthorityData ca,
                                                  final Collection<AnnouncedRoute> toMute,
                                                  final Collection<AnnouncedRoute> toUnmute) {
        commandService.execute(new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(ca.getVersionedId(), toMute, toUnmute));
        return created();
    }

    private Collection<AnnouncedRoute> getAnnouncedRoutes(List<BgpAnnouncement> announcements) {
        return announcements.stream()
                .map(bgp -> new AnnouncedRoute(Asn.parse(bgp.getAsn()), IpRange.parse(bgp.getPrefix()))).toList();
    }

    private void doSubscribe(CaName caName, Subscriptions newSubscription) {

        final Set<String> newEmails = newSubscription.getEmails();
        final Set<RouteValidityState> newValidityStates = newSubscription.getRouteValidityStates().stream()
                .map(RouteValidityState::valueOf)
                .collect(Collectors.toSet());

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final RoaAlertConfigurationData currentConfiguration = roaAlertConfigurationViewService.findRoaAlertSubscription(ca.getId());

        List<? extends CertificateAuthorityCommand> commands = currentConfiguration == null ?
                initAlertConfiguration(ca, newSubscription, newValidityStates, newEmails) :
                updateAlertConfiguration(ca, newSubscription, currentConfiguration, newEmails, newValidityStates);

        commands.forEach(commandService::execute);
    }

    private List<? extends CertificateAuthorityCommand> updateAlertConfiguration(HostedCertificateAuthorityData ca,
                                                                                 Subscriptions newSubscription,
                                                                                 RoaAlertConfigurationData currentConfiguration,
                                                                                 Set<String> newEmails,
                                                                                 Set<RouteValidityState> newValidityStates) {

        final Set<String> currentEmails = currentConfiguration.getEmails() == null ?
                Collections.emptySet() : new HashSet<>(currentConfiguration.getEmails());
        final Set<RouteValidityState> currentValidityStates = currentConfiguration.getRouteValidityStates() == null ?
                Collections.emptySet() : currentConfiguration.getRouteValidityStates();
        final RoaAlertFrequency currentFrequency = currentConfiguration.getSubscription() == null ?
                null : currentConfiguration.getSubscription().getFrequency();

        List<CertificateAuthorityCommand> commands = new ArrayList<>(
                currentEmails.stream()
                        .filter(object -> !newEmails.contains(object))
                        .map(email ->
                                new UnsubscribeFromRoaAlertCommand(ca.getVersionedId(),
                                        email, newSubscription.isNotifyOnRoaChanges()))
                        .toList());

        // If both validity and frequency stay the same, only subscribe additional email.
        if (Objects.equals(newValidityStates, currentValidityStates) && Objects.equals(newSubscription.getFrequency(), currentFrequency)) {
            if (newEmails.equals(currentEmails)) {
                // if emails also stay the same, the only thing that can change is notifyOnRoaChanges flag.
                // In this case issue special command updating only this flag.
                if (newSubscription.isNotifyOnRoaChanges() != currentConfiguration.isNotifyOnRoaChanges()) {
                    commands.add(new UpdateRoaChangeAlertCommand(ca.getVersionedId(), newEmails, newSubscription.isNotifyOnRoaChanges()));
                }
            } else {
                commands.addAll(newEmails.stream()
                        .filter(email -> !currentEmails.contains(email))
                        .map(email ->
                                new SubscribeToRoaAlertCommand(ca.getVersionedId(),
                                        email, newValidityStates,
                                        newSubscription.getFrequency(),
                                        newSubscription.isNotifyOnRoaChanges()))
                        .toList());
            }
        } else {
            if (!newValidityStates.isEmpty() && newSubscription.getFrequency() != null) {
                // Either validity or frequency changes so these guys have to be subscribed.
                commands.addAll(newEmails.stream().map(email ->
                        new SubscribeToRoaAlertCommand(ca.getVersionedId(),
                                email, newValidityStates,
                                newSubscription.getFrequency(),
                                newSubscription.isNotifyOnRoaChanges())).toList());
            }
        }
        return commands;
    }

    private List<? extends CertificateAuthorityCommand> initAlertConfiguration(HostedCertificateAuthorityData ca,
                                                                               Subscriptions newSubscription,
                                                                               Set<RouteValidityState> newValidityStates,
                                                                               Set<String> newEmails) {
        if (newValidityStates.isEmpty() && newSubscription.isNotifyOnRoaChanges()) {
            return List.of(new UpdateRoaChangeAlertCommand(ca.getVersionedId(), newEmails, true));
        }
        return newEmails.stream().map(email ->
                new SubscribeToRoaAlertCommand(ca.getVersionedId(),
                        email, newValidityStates,
                        newSubscription.getFrequency(),
                        newSubscription.isNotifyOnRoaChanges())).toList();
    }

}
