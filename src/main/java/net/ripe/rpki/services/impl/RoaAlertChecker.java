package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.*;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailTokens;
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
        NestedIntervalMap<IpResource, List<RoaConfigurationPrefixData>> allowedRoutes = allowedRoutesToNestedIntervalMap(roaConfiguration.getPrefixes());

        AnnouncedRoutes announcedRoutes = new AnnouncedRoutes();
        announcements.stream().map(BgpRisEntry::toAnnouncedRoute)
            .filter(x -> !ignoredAnnouncements.contains(x))
            .forEach(announcedRoute -> {

                RouteValidityState validityState = RouteOriginValidationPolicy.validateAnnouncedRoute(allowedRoutes, announcedRoute);
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

    private void sendRoaAlertEmailToSubscription(RoaAlertConfigurationData configuration,
                                                 List<AnnouncedRoute> invalidAsnsToMail,
                                                 List<AnnouncedRoute> invalidLengthsToMail,
                                                 List<AnnouncedRoute> unknowns,
                                                 Set<AnnouncedRoute> unsortedIgnoredAlerts) {
        String humanizedCaName = internalNamePresenter.humanizeCaName(configuration.getCertificateAuthority().getName());
        Collections.sort(invalidAsnsToMail);
        Collections.sort(invalidLengthsToMail);
        Collections.sort(unknowns);

        final SortedSet<AnnouncedRoute> ignoredAlerts = new TreeSet<>();
        ignoredAlerts.addAll(unsortedIgnoredAlerts);

        var parameters = Map.of(
            "humanizedCaName", humanizedCaName,
            "ignoredAlerts", ignoredAlerts,
            "invalidAsns", invalidAsnsToMail,
            "invalidLengths", invalidLengthsToMail,
            "unknowns", unknowns,
            "subscription", configuration
        );

        configuration.getSubscription().getEmails().forEach(email -> emailSender.sendEmail(
                email,
                String.format(EmailSender.EmailTemplates.ROA_ALERT.templateSubject, humanizedCaName),
                EmailSender.EmailTemplates.ROA_ALERT,
                parameters,
                EmailTokens.uniqueId(configuration.getCertificateAuthority().getUuid()))
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
