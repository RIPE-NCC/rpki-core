package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;

@Component
@Slf4j
public class RoaAlertChecker {
    private static final String METRIC_NAME = "rpkicore.roa.alert";
    private static final String METRIC_DESCRIPTION = "Number of alert emails sent";

    private final Counter countTriggeredByInvalidAsn;
    private final Counter countTriggeredByInvalidLength;
    private final Counter countTriggeredByUnknown;
    private final Counter countUniqueAlerts;

    private static final String ROA_ALERT_SUBJECT_FORMAT = "Resource Certification (RPKI) alerts for %s";

    private final RoaViewService roaService;
    private final BgpRisEntryViewService bgpRisEntryRepository;
    private final EmailSender emailSender;
    private final InternalNamePresenter internalNamePresenter;

    @Autowired
    public RoaAlertChecker(
            RoaViewService roaService,
            BgpRisEntryViewService bgpRisEntryRepository,
            InternalNamePresenter internalNamePresenter,
            EmailSender emailSender,
            MeterRegistry meterRegistry) {
        this.roaService = roaService;
        this.bgpRisEntryRepository = bgpRisEntryRepository;
        this.internalNamePresenter = internalNamePresenter;
        this.emailSender = emailSender;

        countTriggeredByInvalidAsn = Counter.builder(METRIC_NAME).description(METRIC_DESCRIPTION).tag("type", "invalid asn").register(meterRegistry);
        countTriggeredByInvalidLength = Counter.builder(METRIC_NAME).description(METRIC_DESCRIPTION).tag("type", "invalid length").register(meterRegistry);
        countTriggeredByUnknown = Counter.builder(METRIC_NAME).description(METRIC_DESCRIPTION).tag("type", "unknown").register(meterRegistry);

        countUniqueAlerts = Counter.builder(METRIC_NAME).description(METRIC_DESCRIPTION).tag("type", "unique alerts").register(meterRegistry);
    }

    private void updateMetrics(List<AnnouncedRoute> invalidAsnsToMail, List<AnnouncedRoute> invalidLengthsToMail, List<AnnouncedRoute> unknownsToMail) {
        if (!invalidAsnsToMail.isEmpty()) {
            countTriggeredByInvalidAsn.increment();
        }
        if (!invalidLengthsToMail.isEmpty()) {
            countTriggeredByInvalidLength.increment();
        }
        if (!unknownsToMail.isEmpty()) {
            countTriggeredByUnknown.increment();
        }

        if (!invalidAsnsToMail.isEmpty() || !invalidLengthsToMail.isEmpty() || !unknownsToMail.isEmpty()) {
            countUniqueAlerts.increment();
        }
    }

    public void checkAndSendRoaAlertEmailToSubscription(RoaAlertConfigurationData configuration) {
        if (!configuration.hasSubscription()) {
            return;
        }

        final CertificateAuthorityData ca = configuration.getCertificateAuthority();
        final AnnouncedRoutes announcedRoutes = getAnnouncedRoutesForCA(ca, configuration.getIgnoredAnnouncements());
        final Set<RouteValidityState> routeValidityStates = configuration.getRouteValidityStates();
        final List<AnnouncedRoute> invalidAsnsToMail = routeValidityStates.contains(RouteValidityState.INVALID_ASN) ? announcedRoutes.invalidAsns : Collections.emptyList();
        final List<AnnouncedRoute> invalidLengthsToMail = routeValidityStates.contains(RouteValidityState.INVALID_LENGTH) ? announcedRoutes.invalidLengths : Collections.emptyList();
        final List<AnnouncedRoute> unknownsToMail = routeValidityStates.contains(RouteValidityState.UNKNOWN) ? announcedRoutes.unknowns : Collections.emptyList();
        final Set<AnnouncedRoute> ignoredAnnouncements = configuration.getIgnoredAnnouncements();

        updateMetrics(invalidAsnsToMail, invalidLengthsToMail, unknownsToMail);
        // Ignored announcements do not affect decision of whether to mail or not.
        if (!invalidAsnsToMail.isEmpty() || !invalidLengthsToMail.isEmpty() || !unknownsToMail.isEmpty()) {
            final ImmutableResourceSet caResources = ca.getResources();
            Collection<BgpRisEntry> announcements = bgpRisEntryRepository.findMostSpecificOverlapping(caResources);
            log.info("We are going to send ROA alert to the CA {}. \nIts certified resources are {}, " +
                    "\nannouncements are {}, \nROA configuration is {}, \nannouncedRoutes is {}",
                    ca.getId(), caResources, announcements, configuration, announcedRoutes);

            sendRoaAlertEmailToSubscription(configuration, invalidAsnsToMail, invalidLengthsToMail, unknownsToMail, ignoredAnnouncements);
        }
    }

    AnnouncedRoutes getAnnouncedRoutesForCA(CertificateAuthorityData ca, Set<AnnouncedRoute> ignoredAnnouncements) {
        RoaConfigurationData roaConfiguration = roaService.getRoaConfiguration(ca.getId());
        Collection<BgpRisEntry> announcements = bgpRisEntryRepository.findMostSpecificOverlapping(ca.getResources());
        RouteOriginValidationPolicy policy = new RouteOriginValidationPolicy();
        NestedIntervalMap<IpResource, List<AllowedRoute>> allowedRoutes = allowedRoutesToNestedIntervalMap(roaConfiguration.toAllowedRoutes());

        AnnouncedRoutes announcedRoutes = new AnnouncedRoutes();
        announcements.stream().map(BgpRisEntry::toAnnouncedRoute)
            .filter(x -> !ignoredAnnouncements.contains(x))
            .forEach(announcedRoute -> {

                RouteValidityState validityState = policy.validateAnnouncedRoute(allowedRoutes, announcedRoute);
                switch (validityState) {
                    case VALID:
                        announcedRoutes.valids.add(announcedRoute);
                        break;
                    case UNKNOWN:
                        announcedRoutes.unknowns.add(announcedRoute);
                        break;
                    case INVALID_ASN:
                        announcedRoutes.invalidAsns.add(announcedRoute);
                        break;
                    case INVALID_LENGTH:
                        announcedRoutes.invalidLengths.add(announcedRoute);
                        break;
                }
            });

        return announcedRoutes;
    }

    // TODO Add invalidAsns and invalidLength
    private void sendRoaAlertEmailToSubscription(RoaAlertConfigurationData configuration,
                                                 List<AnnouncedRoute> invalidAsnsToMail,
                                                 List<AnnouncedRoute> invalidLengthsToMail,
                                                 List<AnnouncedRoute> unknowns,
                                                 Set<AnnouncedRoute> unsortedIgnoredAlerts) {
        String humanizedCaName = internalNamePresenter.humanizeCaName(configuration.getCertificateAuthority().getName());
        invalidAsnsToMail.sort(AnnouncedRoute.ASN_PREFIX_COMPARATOR);
        invalidLengthsToMail.sort(AnnouncedRoute.ASN_PREFIX_COMPARATOR);
        unknowns.sort(AnnouncedRoute.ASN_PREFIX_COMPARATOR);

        final SortedSet<AnnouncedRoute> ignoredAlerts = new TreeSet<>(AnnouncedRoute.ASN_PREFIX_COMPARATOR);
        ignoredAlerts.addAll(unsortedIgnoredAlerts);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("humanizedCaName", humanizedCaName);
        parameters.put("ignoredAlerts", ignoredAlerts);
        parameters.put("invalidAsns", invalidAsnsToMail);
        parameters.put("invalidLengths", invalidLengthsToMail);
        parameters.put("unknowns", unknowns);
        parameters.put("subscription", configuration);

        configuration.getSubscription().getEmails().forEach(email -> emailSender.sendEmail(
                email,
                String.format(ROA_ALERT_SUBJECT_FORMAT, humanizedCaName),
                "email-templates/roa-alert-email.txt",
                parameters)
        );
    }

    @ToString
    static final class AnnouncedRoutes {
        final List<AnnouncedRoute> invalidAsns = new ArrayList<>();
        final List<AnnouncedRoute> invalidLengths = new ArrayList<>();
        final List<AnnouncedRoute> unknowns = new ArrayList<>();
        final List<AnnouncedRoute> valids = new ArrayList<>();
    }
}
