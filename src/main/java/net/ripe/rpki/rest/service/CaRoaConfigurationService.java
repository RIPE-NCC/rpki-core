package net.ripe.rpki.rest.service;

import com.google.common.base.Preconditions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.*;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.BgpAnnouncementChange;
import net.ripe.rpki.rest.pojo.PublishSet;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;
import net.ripe.rpki.rest.pojo.ApiRoaPrefixExtended;
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

import static java.util.Map.of;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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
    public ResponseEntity<List<ApiRoaPrefixExtended>> getROAsForCAWithBrokenAnnouncements(@PathVariable("caName") final CaName caName) {
        log.info("REST call: Get all ROAs belonging to CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

        final var roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final var roaPrefixes = roaConfiguration.getPrefixes();

        final Collection<BgpRisEntry> announcements = bgpRisEntryViewService.findMostSpecificOverlapping(certifiedResources);
        final NestedIntervalMap<IpResource, List<RoaConfigurationPrefixData>> allowedRouteMap = allowedRoutesToNestedIntervalMap(roaPrefixes);
        final Set<AnnouncedRoute> ignoredAnnouncements = getIgnoredAnnouncement(roaAlertConfigurationViewService, ca.getId());

        // gather the announcements which are made invalid by some ROAs
        // and don't have ROAs validating them
        final Set<BgpRisEntry> invalidAnnouncements = new HashSet<>();
        for (BgpRisEntry bgp : announcements) {
            final AnnouncedRoute announcedRoute = bgp.toAnnouncedRoute();
            if (!ignoredAnnouncements.contains(announcedRoute)) {
                final RouteValidityState validityState = RouteOriginValidationPolicy.validateAnnouncedRoute(allowedRouteMap, announcedRoute);
                if (validityState == RouteValidityState.INVALID_ASN || validityState == RouteValidityState.INVALID_LENGTH) {
                    invalidAnnouncements.add(bgp);
                }
            }
        }

        final List<ApiRoaPrefixExtended> roas = new ArrayList<>();
        roaPrefixes.stream()
                .sorted()
            .forEach(roaPrefix -> {
                final Collection<BgpRisEntry> announcementsOverlappingWithCurrentPrefix = bgpRisEntryViewService.findMostSpecificOverlapping(ImmutableResourceSet.of(roaPrefix.getPrefix()));
                final NestedIntervalMap<IpResource, List<RoaConfigurationPrefixData>> allowed = allowedRoutesToNestedIntervalMap(Collections.singletonList(roaPrefix));
                int valids = 0;
                int invalids = 0;
                for (BgpRisEntry bgp : announcementsOverlappingWithCurrentPrefix) {
                    final AnnouncedRoute announcedRoute = new AnnouncedRoute(bgp.getOrigin(), bgp.getPrefix());
                    if (!ignoredAnnouncements.contains(announcedRoute)) {
                        final RouteValidityState currentValidityState = RouteOriginValidationPolicy.validateAnnouncedRoute(allowed, announcedRoute);
                        if (invalidAnnouncements.contains(bgp)) {
                            if (currentValidityState == RouteValidityState.INVALID_ASN || currentValidityState == RouteValidityState.INVALID_LENGTH) {
                                invalids++;
                            }
                        } else if (currentValidityState == RouteValidityState.VALID) {
                            valids++;
                        }
                    }
                }
                roas.add(new ApiRoaPrefixExtended(roaPrefix.getAsn().toString(), roaPrefix.getPrefix().toString(), roaPrefix.getMaximumLength(), valids, invalids, roaPrefix.getUpdatedAt()));
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
        final List<RoaConfigurationPrefixData> certifiedRoutes = currentRoaConfiguration.getPrefixes();
        final IpRange announcedPrefix = IpRange.parse(announcement.getPrefix());
        final String announcementAsn = announcement.getAsn();

        final List<ROAWithAnnouncementStatus> affectingROAs = certifiedRoutes.stream()
                .filter(certifiedRoute -> certifiedRoute.getPrefix().contains(announcedPrefix))
                .map(certifiedRoute -> {
                    final String routeAsn = certifiedRoute.getAsn().toString();
                    final ApiRoaPrefix roa = new ApiRoaPrefix(routeAsn, certifiedRoute.getPrefix().toString(), certifiedRoute.getMaximumLength());
                    final RouteValidityState validityState = determineValidityState(announcedPrefix, announcementAsn, certifiedRoute);
                    return new ROAWithAnnouncementStatus(roa, validityState);
                }).toList();

        return ok(affectingROAs);
    }

    /**
     * Determine validity status of announcement given ROA
     * @requires certifiedRoute to contain announcedprefix
     * @return validity state for route
     */
    static RouteValidityState determineValidityState(IpRange announcedPrefix, String announcementAsn, RoaPrefixData certifiedRoute) {
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
                                             @RequestBody final List<ApiRoaPrefix> futureRoasPrefixes) {
        log.info("REST call: Stage ROAs for CA: {}", caName);

        final Optional<String> errorMessage = Utils.errorsInUserInputRoas(futureRoasPrefixes);
        if (errorMessage.isPresent()) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, "New ROAs are not correct: " + errorMessage.get()));
        }

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

        final Map<Boolean, Collection<BgpRisEntry>> bgpAnnouncements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData currentRoaConfiguration = roaViewService.getRoaConfiguration(ca.getId());

        final Set<RoaConfigurationPrefixData> currentRoas = new HashSet<>(currentRoaConfiguration.getPrefixes());

        // MAY throw, but IllegalArgumentExceptions are translated to HTTP 500 through @ControllerAdvice
        List<AllowedRoute> futureRoas = futureRoasPrefixes.stream()
                .map(r -> new AllowedRoute(Asn.parse(r.getAsn()), IpRange.parse(r.getPrefix()), r.getMaxLength()))
                .toList();

        var affectedRanges = buildAffectedRanges(
                futureRoas,
                currentRoas.stream()
                        .map(RoaPrefixData::toAllowedRoute)
                        .collect(Collectors.toSet()));

        Optional<String> validationError = Roas.validateRoaUpdate(futureRoas);
        if (validationError.isPresent()) {
            return ResponseEntity.status(BAD_REQUEST).body(of(ERROR, validationError.get()));
        }

        final List<BgpAnnouncementChange> bgpAnnouncementChanges = getBgpAnnouncementChanges(
                currentRoas, futureRoas, bgpAnnouncements, affectedRanges,
                getIgnoredAnnouncement(roaAlertConfigurationViewService, ca.getId()));

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .eTag(currentRoaConfiguration.entityTag())
            .body(bgpAnnouncementChanges);
    }
    
    static Set<IpRange> buildAffectedRanges(List<AllowedRoute> futureRoas, Set<AllowedRoute> currentRoas) {
        var affectedRanges = new HashSet<IpRange>();
        var futureRoutes = new HashSet<AllowedRoute>();

        for (var roa : futureRoas) {
            futureRoutes.add(roa);
            if (!currentRoas.contains(roa))
                affectedRanges.add(roa.getPrefix());
        }
        for (var roa : currentRoas) {
            if (!futureRoutes.contains(roa))
                affectedRanges.add(roa.getPrefix());
        }
        return affectedRanges;
    }

    static <T extends RoaPrefixData> List<BgpAnnouncementChange> getBgpAnnouncementChanges(
            Collection<T> currentRoutes,
            Collection<AllowedRoute> futureRoutes,
            Map<Boolean, Collection<BgpRisEntry>> bgpAnnouncements,
            Set<IpRange> affectedRanges,
            Set<AnnouncedRoute> ignoredAnnouncements) {

        final NestedIntervalMap<IpResource, List<T>> currentRouteMap = allowedRoutesToNestedIntervalMap(currentRoutes);
        final NestedIntervalMap<IpResource, List<AllowedRoute>> futureRouteMap = allowedRoutesToNestedIntervalMap(futureRoutes);

        final List<BgpAnnouncementChange> result = new ArrayList<>();
        for (Boolean verifiedOrNot : new Boolean[]{true, false}) {
            for (BgpRisEntry bgp : bgpAnnouncements.getOrDefault(verifiedOrNot, Collections.emptyList())) {
                AnnouncedRoute announcedRoute = bgp.toAnnouncedRoute();
                RouteValidityState currentValidityState = RouteOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                RouteValidityState futureValidityState = RouteOriginValidationPolicy.validateAnnouncedRoute(futureRouteMap, announcedRoute);
                boolean isSuppressed = ignoredAnnouncements.contains(announcedRoute);
                boolean affectedByChange = affectedRanges.stream().anyMatch(r -> r.contains(bgp.getPrefix()));
                result.add(new BgpAnnouncementChange(bgp.getOrigin().toString(), bgp.getPrefix().toString(),
                        bgp.getVisibility(), isSuppressed, currentValidityState, futureValidityState,
                        affectedByChange, verifiedOrNot));
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

        for (ApiRoaPrefix roa : publishSet.getAdded()) {
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
            var currentRoas = roaViewService.getRoaConfiguration(ca.getId()).getPrefixes().stream().map(RoaPrefixData::toAllowedRoute).collect(Collectors.toSet());
            var futureRoas = Roas.applyDiff(currentRoas, Roas.toDiff(publishSet));

            Roas.validateRoaUpdate(futureRoas)
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

    private Collection<RoaConfigurationPrefixData> getRoaConfigurationPrefixDatas(final Collection<ApiRoaPrefix> roas) {
        return roas.stream()
                .map(roa -> new RoaConfigurationPrefixData(
                        Asn.parse(roa.getAsn()),
                        IpRange.parse(roa.getPrefix()),
                        roa.getMaxLength())).toList();
    }

    private static Set<AnnouncedRoute> getIgnoredAnnouncement(RoaAlertConfigurationViewService service, Long caId) {
        final RoaAlertConfigurationData roaAlertSubscription = service.findRoaAlertSubscription(caId);
        if (roaAlertSubscription != null && roaAlertSubscription.getIgnoredAnnouncements() != null) {
            return roaAlertSubscription.getIgnoredAnnouncements();
        }
        return Collections.emptySet();
    }
}
