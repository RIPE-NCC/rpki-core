package net.ripe.rpki.rest.service;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.rest.exception.BadRequestException;
import net.ripe.rpki.rest.exception.ObjectNotFoundException;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.ROA;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import org.apache.commons.lang.StringUtils;
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
    private final ResourceCertificateViewService resourceCertificateViewService;
    private final BgpRisEntryViewService bgpRisEntryViewService;
    private final RoaViewService roaViewService;
    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;

    @Autowired
    public AnnouncementService(ResourceCertificateViewService resourceCertificateViewService,
                               BgpRisEntryViewService bgpRisEntryViewService,
                               RoaViewService roaViewService,
                               RoaAlertConfigurationViewService roaAlertConfigurationViewService) {
        this.resourceCertificateViewService = resourceCertificateViewService;
        this.bgpRisEntryViewService = bgpRisEntryViewService;
        this.roaViewService = roaViewService;
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
    }

    @GetMapping
    @Operation(summary = "Get all announcements belonging to a CA")
    public ResponseEntity<List<BgpAnnouncement>> getResourcesForCa(@PathVariable("caName") final String caName) {
        log.info("Getting resources for CA: {}", caName);

        if (StringUtils.isEmpty(caName)) {
            throw new BadRequestException("Passed CA name is empty.");
        }

        final IpResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(this.getCaId());
        if (certifiedResources == null) {
            throw new ObjectNotFoundException(String.format("unknown CA: %s", caName));
        }
        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(this.getCaId());
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, getCaId());
        return ok(Utils.makeBgpAnnouncementList(announcements, roaConfiguration.toAllowedRoutes(), ignoredAnnouncements));
    }

    @PostMapping("/affected")
    @Operation(summary = "Get all announcements affected by the given ROA configuration of a CA")
    public ResponseEntity<List<BgpAnnouncement>> getAffectedAnnouncementsForCaAndRoa(@PathVariable("caName") final String caName, @RequestBody final ROA roa) {
        log.info("Get all announcements affected by the given ROA configuration for CA: {}", getRawCaName());

        final Optional<String> addedRoasErrorMessage = Utils.errorsInUserInputRoas(roa);
        if (addedRoasErrorMessage.isPresent()) {
            throw new BadRequestException(String.format("Passed ROA is incorrect: %s", addedRoasErrorMessage.get()));
        }

        IpResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(getCaId());
        if (certifiedResources == null) {
            throw new ObjectNotFoundException(String.format("Could find any resources belonging to CA: %s", getRawCaName()));
        }

        final Asn roaAsn = Asn.parse(roa.getAsn());
        final IpRange roaPrefix = IpRange.parse(roa.getPrefix());

        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.
            findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(this.getCaId());
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, getCaId());

        final Set<AnnouncedRoute> routesValidatedByOthers = new HashSet<>();
        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(roaConfiguration.toAllowedRoutes());
        final RouteOriginValidationPolicy routeOriginValidationPolicy = new RouteOriginValidationPolicy();
        Stream.of(true, false)
                .filter(announcements::containsKey)
                .flatMap(verifiedOrNot -> announcements.get(verifiedOrNot).stream())
                .map(announcement -> new AnnouncedRoute(announcement.getOrigin(), announcement.getPrefix()))
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
