package net.ripe.rpki.rest.service;

import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.rest.exception.BadRequestException;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.ROA;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/announcements", produces = { APPLICATION_JSON })
@Tag(name = "/ca/{caName}/announcements", description = "View of CA announcements")
public class AnnouncementService extends AbstractCaRestService {
    private final BgpRisEntryViewService bgpRisEntryViewService;
    private final RoaViewService roaViewService;
    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;

    @Autowired
    public AnnouncementService(BgpRisEntryViewService bgpRisEntryViewService,
                               RoaViewService roaViewService,
                               RoaAlertConfigurationViewService roaAlertConfigurationViewService) {
        this.bgpRisEntryViewService = bgpRisEntryViewService;
        this.roaViewService = roaViewService;
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
    }

    @GetMapping
    @Operation(summary = "Get all announcements, as well as not-announced ignored announcements for the CA")
    public ResponseEntity<List<BgpAnnouncement>> getResourcesForCa(@PathVariable("caName") final CaName caName) {
        log.info("Getting resources for CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();
        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, ca.getId());

        final List<BgpAnnouncement> announcedAnnouncements = Utils.makeBgpAnnouncementList(announcements, roaConfiguration.toAllowedRoutes(), ignoredAnnouncements);

        // ignoredAnnouncements \ bgp announcements
        final Set<AnnouncedRoute> notSeenIgnoredAnnouncedRoutes = Sets.difference(ignoredAnnouncements, bgpRisMapToAnnouncedRoutes(announcements));

        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(roaConfiguration.toAllowedRoutes());

        // Create synthetic 'not seen' announcements for which a suppression exists.
        final List<BgpAnnouncement> notSeenAnnouncements = notSeenIgnoredAnnouncedRoutes.stream().map(
                ar -> new BgpAnnouncement(ar.getOriginAsn().toString(), ar.getPrefix().toString(),
                        0, Utils.ROUTE_VALIDATION_POLICY.validateAnnouncedRoute(currentRouteMap, ar),
                        true)
        ).collect(Collectors.toList());

        return ok(Stream.concat(announcedAnnouncements.stream(), notSeenAnnouncements.stream()).collect(Collectors.toList()));
    }

    private Set<AnnouncedRoute> bgpRisMapToAnnouncedRoutes(Map<Boolean, Collection<BgpRisEntry>> announcements) {
        return announcements.values().stream()
                .flatMap(Collection::stream)
                .map(BgpRisEntry::toAnnouncedRoute)
                .collect(Collectors.toSet());
    }


    @PostMapping("/affected")
    @Operation(summary = "Get all announcements affected by the given ROA configuration of a CA")
    public ResponseEntity<List<BgpAnnouncement>> getAffectedAnnouncementsForCaAndRoa(@PathVariable("caName") final CaName caName, @RequestBody final ROA roa) {
        log.info("Get all announcements affected by the given ROA configuration for CA: {}", caName);

        final Optional<String> addedRoasErrorMessage = Utils.errorsInUserInputRoas(roa);
        if (addedRoasErrorMessage.isPresent()) {
            throw new BadRequestException(String.format("Passed ROA is incorrect: %s", addedRoasErrorMessage.get()));
        }

        HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        ImmutableResourceSet certifiedResources = ca.getResources();

        final Asn roaAsn = Asn.parse(roa.getAsn());
        final IpRange roaPrefix = IpRange.parse(roa.getPrefix());

        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.
            findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, ca.getId());

        final Set<AnnouncedRoute> routesValidatedByOthers = new HashSet<>();
        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(roaConfiguration.toAllowedRoutes());
        final RouteOriginValidationPolicy routeOriginValidationPolicy = new RouteOriginValidationPolicy();
        Stream.of(true, false)
                .filter(announcements::containsKey)
                .flatMap(verifiedOrNot -> announcements.get(verifiedOrNot).stream())
                .map(BgpRisEntry::toAnnouncedRoute)
                .forEach(announcedRoute -> {
                    final RouteValidityState currentValidityState = routeOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                    if (currentValidityState == RouteValidityState.VALID &&
                            !(roaAsn.equals(announcedRoute.getOriginAsn()) && roaPrefix.equals(announcedRoute.getPrefix()))) {
                        routesValidatedByOthers.add(announcedRoute);
                    }
                });

        final List<BgpAnnouncement> bgpAnnouncements = Utils.makeBgpAnnouncementList(announcements, Collections.singletonList(
                new AllowedRoute(roaAsn, roaPrefix, roa.getMaxLength())),
                ignoredAnnouncements);

        final List<BgpAnnouncement> knownAnnouncements = new ArrayList<>();
        for (BgpAnnouncement announcement : bgpAnnouncements) {
            final AnnouncedRoute announcedRoute = new AnnouncedRoute(Asn.parse(announcement.getAsn()), IpRange.parse(announcement.getPrefix()));
            if (announcement.getCurrentState() == RouteValidityState.VALID ||
                    ((announcement.getCurrentState() == RouteValidityState.INVALID_ASN ||
                    announcement.getCurrentState() == RouteValidityState.INVALID_LENGTH) &&
                    !routesValidatedByOthers.contains(announcedRoute))) {
                knownAnnouncements.add(announcement);
            }
        }
        return ok(knownAnnouncements);
    }

}
