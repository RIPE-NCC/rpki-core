package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.CaStatus;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.collect.ImmutableMap.of;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Used by demographics-collector
 */
@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = "/api/ca-stat", produces = MediaType.APPLICATION_JSON)
@Tag(name = "/api/ca-stat", description = "Statistics on CAs")
public class CaStatService extends RestService {
    private final ResourceCertificateViewService resourceCertificateViewService;
    private final BgpRisEntryViewService bgpRisEntryViewService;
    private final RoaViewService roaViewService;
    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    private final CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    public CaStatService(ResourceCertificateViewService resourceCertificateViewService,
                         BgpRisEntryViewService bgpRisEntryViewService,
                         RoaViewService roaViewService,
                         RoaAlertConfigurationViewService roaAlertConfigurationViewService,
                         CertificateAuthorityViewService certificateAuthorityViewService) {
        this.resourceCertificateViewService = resourceCertificateViewService;
        this.bgpRisEntryViewService = bgpRisEntryViewService;
        this.roaViewService = roaViewService;
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
        this.certificateAuthorityViewService = certificateAuthorityViewService;
    }

    @GetMapping(path = "status/{caName}")
   @Operation(summary = "Return generic status of the CA, ROAs number, etc.")
    public ResponseEntity<?> status(@PathVariable("caName") final String rawCaName) {
        CertificateAuthorityData caByName = getCaByName(rawCaName);
        if (caByName == null) {
            return ResponseEntity.status(NOT_FOUND).body(of("error", "unknown CA: " + rawCaName));
        }
        final long caId = caByName.getId();
        final ImmutableResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(caId);
        if (certifiedResources == null) {
            return ResponseEntity.status(NOT_FOUND).body(of("error", "CA doesn't have resources, CA: " + rawCaName));
        }

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(getCaStatus(caId, certifiedResources));
    }

    @GetMapping(path = "statuses")
   @Operation(summary = "Return generic status of the CA, ROAs number, etc.")
    public ResponseEntity<?> statuses(@RequestParam("caNames") final String rawCaNames) {
        if (rawCaNames == null) {
            return ResponseEntity.status(BAD_REQUEST)
                    .body(of("error", "'caNames' parameter is not provided"));
        }
        log.info("Getting status for a bunch of CAs...");
        final String[] rawCaNameArray = rawCaNames.split("\\s*,\\s*");
        final Map<String, Object> result = new ConcurrentHashMap<>();
        Arrays.stream(rawCaNameArray).parallel().forEachOrdered(n -> {
            try {
                final CaName caName = CaName.parse(n.trim());

                final CertificateAuthorityData caByName =
                        certificateAuthorityViewService.findCertificateAuthorityByName(caName.getPrincipal());

                if (caByName == null) {
                    result.put(caName.toString(), Collections.singletonMap("error", "Unknown CA: " + caName));
                } else {
                    final long caId = caByName.getId();
                    final ImmutableResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(caId);
                    if (certifiedResources == null) {
                        result.put(caName.toString(), Collections.singletonMap("error", "Ca doesn't have resources, CA: " + caName));
                    } else {
                        final CaStatus caStatus = getCaStatus(caId, certifiedResources);
                        result.put(caName.toString(), caStatus);
                    }
                }
            } catch (Exception e) {
                result.put(n, Collections.singletonMap("error", "Couldn't parse CA name: " + n));
            }
        });

        log.info("Done.");
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(result);
    }

    private CaStatus getCaStatus(long caId, ImmutableResourceSet certifiedResources) {
        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryViewService.findMostSpecificContainedAndNotContained(certifiedResources);
        final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(caId);
        final Set<AnnouncedRoute> ignoredAnnouncements = Utils.getIgnoredAnnouncements(roaAlertConfigurationViewService, caId);
        final List<BgpAnnouncement> bgpAnnouncements = Utils.makeBgpAnnouncementList(announcements, roaConfiguration.getPrefixes(), ignoredAnnouncements);

        int valid = 0, invalid = 0, unknown = 0;
        for (BgpAnnouncement bgpAnnouncement : bgpAnnouncements) {
            RouteValidityState validityState = bgpAnnouncement.getCurrentState();
            if (validityState == RouteValidityState.VALID) {
                valid++;
            } else if (validityState == RouteValidityState.INVALID_ASN ||
                    validityState == RouteValidityState.INVALID_LENGTH) {
                invalid++;
            } else if (validityState == RouteValidityState.UNKNOWN) {
                unknown++;
            }
        }

        final CaStatus caStatus = new CaStatus();
        caStatus.setAnnouncementsValid(valid);
        caStatus.setAnnouncementsInvalid(invalid);
        caStatus.setAnnouncementsUnknown(unknown);
        caStatus.setRoaNumber(roaConfiguration.getPrefixes().size());

        return caStatus;
    }

    @GetMapping(path = "all")
    @Operation(summary = "Return all CAs")
    public ResponseEntity<Collection<CaStat>> allCAs() {
        log.info("Getting stats for all CAs");
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(certificateAuthorityViewService.getCaStats());
    }

    @GetMapping(path = "events")
    @Operation(summary = "Return all CAs")
    public ResponseEntity<Collection<? extends CaStatEvent>> events() {
        log.info("Getting all events for all CAs");
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(certificateAuthorityViewService.getCaStatEvents());
    }

    private CertificateAuthorityData getCaByName(String unparsedCaName) {
        CaName caName = CaName.parse(unparsedCaName);
        return certificateAuthorityViewService.findCertificateAuthorityByName(caName.getPrincipal());
    }
}
