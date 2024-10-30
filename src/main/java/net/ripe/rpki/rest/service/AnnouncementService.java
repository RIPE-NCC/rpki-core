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
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.rest.exception.BadRequestException;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
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

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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

    /**
     * @deprecated This end-point only exists for the current RPKI dashboard. Remove this call as soon as either it's
     * not used from ripe-portal anymore or the whole ripe-portal-based RPKI dashboard is not used anymore.
     */
    @GetMapping
    @Operation(summary = "Get all announcements, as well as not-announced ignored announcements for the CA", deprecated = true)
    @Deprecated(since = "2024-07-17", forRemoval = true)
    public ResponseEntity<List<BgpAnnouncement>> getResourcesForCa(@PathVariable("caName") final CaName caName) {
        log.info("Getting resources for CA: {}", caName);
        var response = getAnnouncements(caName);
        if (response instanceof AnnouncementResponse.Announcements as) {
            return ok(as.announcements);
        }
        return ok(Collections.emptyList());
    }

    @GetMapping("extended")
    @Operation(summary = "Get all announcements, metadata for them and not-announced ignored announcements for the CA")
    public ResponseEntity<AnnouncementResponse> getResourcesForCaWithMetadata(@PathVariable("caName") final CaName caName) {
        log.info("Getting resources for CA: {}", caName);
        return ok(getAnnouncements(caName));
    }

    private AnnouncementResponse getAnnouncements(CaName caName) {
        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();
        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, ca.getId());

        final List<BgpAnnouncement> announcedAnnouncements = Utils.makeBgpAnnouncementList(announcements, roaConfiguration.getPrefixes(), ignoredAnnouncements);

        // ignoredAnnouncements \ bgp announcements
        final Set<AnnouncedRoute> notSeenIgnoredAnnouncedRoutes = Sets.difference(ignoredAnnouncements, bgpRisMapToAnnouncedRoutes(announcements));

        final NestedIntervalMap<IpResource, List<RoaConfigurationPrefixData>> currentRouteMap = allowedRoutesToNestedIntervalMap(roaConfiguration.getPrefixes());

        // Create synthetic 'not seen' announcements for which a suppression exists.
        final List<BgpAnnouncement> notSeenAnnouncements = notSeenIgnoredAnnouncedRoutes.stream().map(
                ar -> new BgpAnnouncement(ar.getOriginAsn().toString(), ar.getPrefix().toString(),
                        0, RouteOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, ar),
                        true)
        ).toList();

        Instant risLastUpdated = bgpRisEntryViewService.getLastUpdated();
        Function<String, AnnouncementResponse> reportProblem =
                problem -> risLastUpdated == null ?
                        new AnnouncementResponse.Problem(problem) :
                        new AnnouncementResponse.ProblemWithTimestamp(problem, risLastUpdated);

        if (certifiedResources.isEmpty())
            return reportProblem.apply(NO_CA_RESOURCES);
        else if (risLastUpdated == null)
            return reportProblem.apply(NO_RIS_UPDATES);
        else if (!announcements.isEmpty() &&
                announcements.values().stream().allMatch(Collection::isEmpty))
            return reportProblem.apply(NO_OVERLAP_WITH_RIS);

        var announcement = Stream.concat(announcedAnnouncements.stream(), notSeenAnnouncements.stream()).toList();
        return new AnnouncementResponse.Announcements(announcement, risLastUpdated);
    }

    private Set<AnnouncedRoute> bgpRisMapToAnnouncedRoutes(Map<Boolean, Collection<BgpRisEntry>> announcements) {
        return announcements.values().stream()
                .flatMap(Collection::stream)
                .map(BgpRisEntry::toAnnouncedRoute)
                .collect(Collectors.toSet());
    }

    @PostMapping("/affected")
    @Operation(summary = "Get all announcements affected by the given ROA configuration of a CA")
    public ResponseEntity<List<BgpAnnouncement>> getAffectedAnnouncementsForCaAndRoa(@PathVariable("caName") final CaName caName, @RequestBody final ApiRoaPrefix roa) {
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

        final List<BgpAnnouncement> affectedAnnouncements = Utils.getAffectedAnnouncements(
                roaConfiguration, announcements, ignoredAnnouncements, roaAsn, roaPrefix, roa.getMaxLength());

        return ok(affectedAnnouncements);
    }

    public static final String NO_CA_RESOURCES = "no-ca-resources";
    public static final String NO_RIS_UPDATES = "no-ris-updates";
    public static final String NO_OVERLAP_WITH_RIS = "no-overlap-with-ris";

    public interface AnnouncementResponse {
        record Problem(String emptyAnnouncementsReason) implements AnnouncementResponse {}
        record ProblemWithTimestamp(String emptyAnnouncementsReason, Instant lastUpdated) implements AnnouncementResponse {}

        record Announcements(List<BgpAnnouncement> announcements,
                             Instant lastUpdated) implements AnnouncementResponse { }
    }

}
