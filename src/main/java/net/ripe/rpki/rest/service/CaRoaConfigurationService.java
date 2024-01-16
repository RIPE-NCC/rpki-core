package net.ripe.rpki.rest.service;

import com.google.common.base.Preconditions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.BgpAnnouncementChange;
import net.ripe.rpki.rest.pojo.PublishSet;
import net.ripe.rpki.rest.pojo.ROA;
import net.ripe.rpki.rest.pojo.ROAExtended;
import net.ripe.rpki.rest.pojo.ROAWithAnnouncementStatus;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/roas", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/roas")
public class CaRoaConfigurationService extends AbstractCaRestService {
    public static final String ERROR = "error";
    private final RoaViewService roaViewService;
    private final BgpRisEntryViewService bgpRisEntryViewService;
    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    private final CommandService commandService;

    @Autowired
    public CaRoaConfigurationService(RoaViewService roaViewService,
                                     RoaConfigurationRepository roaConfigurationRepository,
                                     BgpRisEntryViewService bgpRisEntryViewService,
                                     RoaAlertConfigurationViewService roaAlertConfigurationViewService,
                                     CommandService commandService) {
        this.roaViewService = roaViewService;
        this.bgpRisEntryViewService = bgpRisEntryViewService;
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Get all ROAs belonging to a CA")
    public ResponseEntity<List<ROAExtended>> getROAsForCAWithBrokenAnnouncements(@PathVariable("caName") final CaName caName) {
        log.info("REST call: Get all ROAs belonging to CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final RouteOriginValidationPolicy routeOriginValidationPolicy = new RouteOriginValidationPolicy();

        final Collection<BgpRisEntry> announcements = bgpRisEntryViewService.findMostSpecificOverlapping(certifiedResources);
        final List<AllowedRoute> allowedRoutes = roaConfiguration.toAllowedRoutes();
        final NestedIntervalMap<IpResource, List<AllowedRoute>> allowedRouteMap = allowedRoutesToNestedIntervalMap(allowedRoutes);
        final Set<AnnouncedRoute> ignoredAnnouncements = getIgnoredAnnouncement(ca.getId());

        // gather the announcements which are made invalid by some ROAs
        // and don't have ROAs validating them
        final Set<BgpRisEntry> invalidAnnouncements = new HashSet<>();
        for (BgpRisEntry bgp : announcements) {
            final AnnouncedRoute announcedRoute = bgp.toAnnouncedRoute();
            if (!ignoredAnnouncements.contains(announcedRoute)) {
                final RouteValidityState validityState = routeOriginValidationPolicy.validateAnnouncedRoute(allowedRouteMap, announcedRoute);
                if (validityState == RouteValidityState.INVALID_ASN || validityState == RouteValidityState.INVALID_LENGTH) {
                    invalidAnnouncements.add(bgp);
                }
            }
        }

        final List<ROAExtended> roas = new ArrayList<>();
        allowedRoutes.stream()
            .sorted(Comparator
                .comparing(ar -> ((AllowedRoute) ar).getAsn().longValue())
                .thenComparing(ar -> ((AllowedRoute) ar).getPrefix()))
            .forEach(allowedRoute -> {
                final Collection<BgpRisEntry> overlappingAnnouncements = bgpRisEntryViewService.findMostSpecificOverlapping(ImmutableResourceSet.of(allowedRoute.getPrefix()));
                final NestedIntervalMap<IpResource, List<AllowedRoute>> allowed = allowedRoutesToNestedIntervalMap(Collections.singletonList(allowedRoute));
                int valids = 0;
                int invalids = 0;
                for (BgpRisEntry bgp : overlappingAnnouncements) {
                    final AnnouncedRoute announcedRoute = new AnnouncedRoute(bgp.getOrigin(), bgp.getPrefix());
                    if (!ignoredAnnouncements.contains(announcedRoute)) {
                        final RouteValidityState currentValidityState = routeOriginValidationPolicy.validateAnnouncedRoute(allowed, announcedRoute);
                        if (invalidAnnouncements.contains(bgp)) {
                            if (currentValidityState == RouteValidityState.INVALID_ASN || currentValidityState == RouteValidityState.INVALID_LENGTH) {
                                invalids++;
                            }
                        } else if (currentValidityState == RouteValidityState.VALID) {
                            valids++;
                        }
                    }
                }
                roas.add(new ROAExtended(allowedRoute.getAsn().toString(), allowedRoute.getPrefix().toString(), allowedRoute.getMaximumLength(), valids, invalids));
            });
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .eTag(roaConfiguration.entityTag())
            .body(roas);
    }

    @PostMapping(path = "affecting")
    @Operation(summary = "Get all ROAs affecting given BGP announcements of a CA")
    public ResponseEntity<List<ROAWithAnnouncementStatus>> getAffectingROAsForCA(@PathVariable("caName") final CaName caName,
                                                                                 @RequestBody final BgpAnnouncement announcement) {
        log.info("REST call: Get ROAs affecting given BGP announcements for CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final RoaConfigurationData currentRoaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final List<AllowedRoute> certifiedRoutes = currentRoaConfiguration.toAllowedRoutes();
        final IpRange announcedPrefix = IpRange.parse(announcement.getPrefix());
        final String announcementAsn = announcement.getAsn();

        final List<ROAWithAnnouncementStatus> affectingROAs = certifiedRoutes.stream()
                .filter(certifiedRoute -> certifiedRoute.getPrefix().contains(announcedPrefix))
                .map(certifiedRoute -> {
                    final String routeAsn = certifiedRoute.getAsn().toString();
                    final ROA roa = new ROA(routeAsn, certifiedRoute.getPrefix().toString(), certifiedRoute.getMaximumLength());
                    final RouteValidityState validityState = determineValidityState(announcedPrefix, announcementAsn, certifiedRoute);
                    return new ROAWithAnnouncementStatus(roa, validityState);
                })
                .collect(Collectors.toList());

        return ok(affectingROAs);
    }

    /**
     * Determine validity status of announcement given ROA
     * @requires certifiedRoute to contain announcedprefix
     * @return validity state for route
     */
    static RouteValidityState determineValidityState(IpRange announcedPrefix, String announcementAsn, AllowedRoute certifiedRoute) {
        Preconditions.checkArgument(certifiedRoute.getPrefix().contains(announcedPrefix), "prefix of route %s is not contained by allowed route %s", announcedPrefix, certifiedRoute.getPrefix());

        if (!certifiedRoute.getAsn().equals(Asn.parse(announcementAsn))) {
            return RouteValidityState.INVALID_ASN;
        } else if (certifiedRoute.getMaximumLength() < announcedPrefix.getPrefixLength()) {
            return RouteValidityState.INVALID_LENGTH;
        }
        return RouteValidityState.VALID;
    }

    @PostMapping(path = "stage")
    @Operation(summary = "Stage ROA changes for the given CA")
    public ResponseEntity<?> stageRoaChanges(@PathVariable("caName") final CaName caName,
                                             @RequestBody final List<ROA> futureRoas) {
        log.info("REST call: Stage ROAs for CA: {}", caName);

        final Optional<String> errorMessage = Utils.errorsInUserInputRoas(futureRoas);
        if (errorMessage.isPresent()) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, "New ROAs are not correct: " + errorMessage.get()));
        }

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

        final Map<Boolean, Collection<BgpRisEntry>> bgpAnnouncements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData currentRoaConfiguration = roaViewService.getRoaConfiguration(ca.getId());

        final Set<AllowedRoute> currentRoutes = new HashSet<>(currentRoaConfiguration.toAllowedRoutes());
        final Set<AllowedRoute> futureRoutes = new HashSet<>(futureRoas.size());
        IpResourceSet affectedRanges;
        try {
            affectedRanges = buildAffectedRanges(futureRoas, futureRoutes, currentRoutes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(BAD_REQUEST).body(Map.of(ERROR, e.getMessage()));
        }

        final List<BgpAnnouncementChange> bgpAnnouncementChanges = getBgpAnnouncementChanges(
                ca, currentRoutes, futureRoutes, bgpAnnouncements, affectedRanges);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .eTag(currentRoaConfiguration.entityTag())
            .body(bgpAnnouncementChanges);
    }

    private IpResourceSet buildAffectedRanges(List<ROA> futureRoas, Set<AllowedRoute> futureRoutes, Set<AllowedRoute> currentRoutes) {
        final IpResourceSet affectedRanges = new IpResourceSet();
        for (ROA roa : futureRoas) {
            final IpRange roaIpRange = IpRange.parse(roa.getPrefix());
            final AllowedRoute route = new AllowedRoute(Asn.parse(roa.getAsn()), roaIpRange, roa.getMaxLength());
            futureRoutes.add(route);
            if (!currentRoutes.contains(route))
                affectedRanges.add(roaIpRange);
        }

        final Map<AnnouncedRoute, List<Integer>> futureRoaMap = Utils.makeROAMap(futureRoas);
        Optional<String> e = Utils.validateUniqueROAs("Error in future ROAs", futureRoaMap);
        if (e.isPresent()) {
            throw new IllegalArgumentException(e.get());
        }

        for (AllowedRoute route : currentRoutes) {
            if (!futureRoutes.contains(route))
                affectedRanges.add(route.getPrefix());

            final AnnouncedRoute key = new AnnouncedRoute(route.getAsn(), route.getPrefix());
            if (futureRoaMap.containsKey(key)) {
                futureRoaMap.get(key).forEach(futureMaxLength -> {
                    if (!Objects.equals(futureMaxLength, route.getMaximumLength())) {
                        // the same ASN+prefix and different max length
                        throw new IllegalArgumentException(Utils.getSameROAErrorMessage(route, key, futureMaxLength));
                    }
                });
            }
        }
        return affectedRanges;
    }

    private List<BgpAnnouncementChange> getBgpAnnouncementChanges(HostedCertificateAuthorityData ca,
                                                                  Set<AllowedRoute> currentRoutes,
                                                                  Set<AllowedRoute> futureRoutes,
                                                                  Map<Boolean, Collection<BgpRisEntry>> bgpAnnouncements,
                                                                  IpResourceSet affectedRanges) {
        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(currentRoutes);
        final NestedIntervalMap<IpResource, List<AllowedRoute>> futureRouteMap = allowedRoutesToNestedIntervalMap(futureRoutes);
        final Set<AnnouncedRoute> ignoredAnnouncements = getIgnoredAnnouncement(ca.getId());

        final RouteOriginValidationPolicy routeOriginValidationPolicy = new RouteOriginValidationPolicy();
        final List<BgpAnnouncementChange> result = new ArrayList<>();
        for (Boolean verifiedOrNot : new Boolean[]{true, false}) {
            if (bgpAnnouncements.containsKey(verifiedOrNot)) {
                for (BgpRisEntry bgp : bgpAnnouncements.get(verifiedOrNot)) {
                    final AnnouncedRoute announcedRoute = bgp.toAnnouncedRoute();
                    final RouteValidityState currentValidityState = routeOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                    final RouteValidityState futureValidityState = routeOriginValidationPolicy.validateAnnouncedRoute(futureRouteMap, announcedRoute);
                    final boolean isSuppressed = ignoredAnnouncements.contains(announcedRoute);
                    result.add(new BgpAnnouncementChange(bgp.getOrigin().toString(), bgp.getPrefix().toString(),
                            bgp.getVisibility(), isSuppressed, currentValidityState, futureValidityState,
                            affectedRanges.contains(bgp.getPrefix()),
                            verifiedOrNot));
                }
            }
        }
        return result;
    }

    @PostMapping(path = "publish")
    @Operation(summary = "Publish ROA changes for the given CA")
    public ResponseEntity<?> publishROAs(@PathVariable("caName") final CaName caName,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatchHeader,
                                         @RequestBody final PublishSet publishSet) {
        log.info("REST call: Publish changes in ROAs for CA: {}", caName);

        if (publishSet.getAdded().isEmpty() && publishSet.getDeleted().isEmpty()) {
            return noContent();
        }

        final Optional<String> addedRoasErrorMessage = Utils.errorsInUserInputRoas(publishSet.getAdded());
        if (addedRoasErrorMessage.isPresent()) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, "Added ROAs are incorrect: " + addedRoasErrorMessage.get()));
        }

        final Optional<String> removedRoasErrorMessage = Utils.errorsInUserInputRoas(publishSet.getDeleted());
        if (removedRoasErrorMessage.isPresent()) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, "Deleted ROAs are incorrect: " + removedRoasErrorMessage.get()));
        }

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

        for (ROA roa : publishSet.getAdded()) {
            PrefixValidationResult validationResult = validatePrefix(roa.getPrefix(), certifiedResources);
            if (PrefixValidationResult.OWNERSHIP_ERROR == validationResult) {
                return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, validationResult.getMessage(roa.getPrefix())));
            }
        }

        if (ifMatchHeader != null && publishSet.getIfMatch() != null && !ifMatchHeader.equals(publishSet.getIfMatch())) {
            return badRequest("`If-Match` header and `ifMatch` field do not match");
        }
        final String ifMatch = StringUtils.defaultIfEmpty(ifMatchHeader, publishSet.getIfMatch());

        try {
            Utils.validateNoIdenticalROAs(
                            roaViewService.getRoaConfiguration(ca.getId()),
                            publishSet.getAdded(), publishSet.getDeleted())
                    .ifPresent(rc -> {
                        throw new IllegalArgumentException(rc);
                    });

            commandService.execute(new UpdateRoaConfigurationCommand(
                    ca.getVersionedId(),
                    Optional.ofNullable(ifMatch),
                    getRoaConfigurationPrefixDatas(publishSet.getAdded()),
                    getRoaConfigurationPrefixDatas(publishSet.getDeleted())
            ));
            return noContent();
        } catch (Exception e) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, e.getMessage()));
        }
    }

    private Collection<RoaConfigurationPrefixData> getRoaConfigurationPrefixDatas(final Collection<ROA> roas) {
        return roas.stream()
                .map(roa -> new RoaConfigurationPrefixData(
                        Asn.parse(roa.getAsn()),
                        IpRange.parse(roa.getPrefix()),
                        roa.getMaxLength()))
                .collect(Collectors.toList());
    }

    private Set<AnnouncedRoute> getIgnoredAnnouncement(Long caId) {
        final RoaAlertConfigurationData roaAlertSubscription = roaAlertConfigurationViewService.findRoaAlertSubscription(caId);
        if (roaAlertSubscription != null && roaAlertSubscription.getIgnoredAnnouncements() != null) {
            return roaAlertSubscription.getIgnoredAnnouncements();
        }
        return Collections.emptySet();
    }
}
