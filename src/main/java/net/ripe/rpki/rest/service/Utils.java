package net.ripe.rpki.rest.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.*;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.stream.Stream;

import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;

@UtilityClass
class Utils {
    static <T extends RoaPrefixData> List<BgpAnnouncement> makeBgpAnnouncementList(Map<Boolean, Collection<BgpRisEntry>> announcements,
                                                                                   Iterable<T> currentAllowedRoutes,
                                                                                   Set<AnnouncedRoute> ignoredAnnouncements) {

        final NestedIntervalMap<IpResource, List<T>> currentRouteMap = allowedRoutesToNestedIntervalMap(currentAllowedRoutes);

        // Verified announcements first, then the rest.
        return Stream.of(true, false)
                .flatMap(verifiedOrNot -> announcements.getOrDefault(verifiedOrNot, Collections.emptyList())
                        .stream()
                        .map(announcement -> {
                            // Create BgbAnnouncement, this needs the BgpRisEntry as well as the derived AnnouncedRoute
                            final AnnouncedRoute announcedRoute = announcement.toAnnouncedRoute();

                            final RouteValidityState currentValidityState = RouteOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                            final boolean isSuppressed = ignoredAnnouncements.contains(announcedRoute);
                            return new BgpAnnouncement(
                                    announcement.getOrigin().toString(), announcement.getPrefix().toString(),
                                    announcement.getVisibility(), currentValidityState,
                                    isSuppressed, verifiedOrNot);
                        }))
                .toList();
    }

    static Set<AnnouncedRoute> getIgnoredAnnouncements(RoaAlertConfigurationViewService roaAlertConfigurationViewService, long caId) {
        final RoaAlertConfigurationData roaAlertSubscription = roaAlertConfigurationViewService.findRoaAlertSubscription(caId);
        if (roaAlertSubscription == null) {
            return Collections.emptySet();
        }
        final Set<AnnouncedRoute> ignoredAnnouncements = roaAlertSubscription.getIgnoredAnnouncements();
        if (ignoredAnnouncements == null) {
            return Collections.emptySet();
        }
        return ignoredAnnouncements;
    }

    public static Optional<String> errorsInUserInputRoas(final List<ApiRoaPrefix> roas) {
        return errorsInUserInputRoas(roas.stream());
    }

    public static Optional<String> errorsInUserInputRoas(final ApiRoaPrefix... roas) {
        return errorsInUserInputRoas(Stream.of(roas));
    }

    private static Optional<String> errorsInUserInputRoas(final Stream<ApiRoaPrefix> roas) {
        final List<String> errors = new ArrayList<>();
        roas.forEach(r -> {
            if (r == null) {
                errors.add("ROA is null");
            } else {
                validateAsn(errors, r);
                validatePrefixAndMaxLength(errors, r);
            }
        });
        return errors.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", errors));
    }

    private static void validateAsn(List<String> errors, @NonNull ApiRoaPrefix r) {
        if (r.getAsn() == null) {
            errors.add("ASN is empty in (" + r + ")");
        } else {
            try {
                Asn.parse(r.getAsn());
            } catch (Exception e) {
                errors.add("ASN '" + r.getAsn() + "' is invalid in (" + r + ")");
            }
        }
    }

    private static void validatePrefixAndMaxLength(List<String> errors, @NonNull ApiRoaPrefix r) {
        if (r.getPrefix() == null) {
            errors.add("Prefix is empty in (" + r + ")");
        } else {
            try {
                IpRange range = IpRange.parse(r.getPrefix());

                if (!range.isLegalPrefix()) {
                    errors.add(String.format("Range '%s' is not a valid prefix", range));
                } else if (r.getMaxLength() == null) {
                    errors.add(String.format("Max length must be specified and must be between %d and %d for prefix '%s'", range.getPrefixLength(), range.getType().getBitSize(), range));
                } else if (!maxLengthIsValid(range, r.getMaxLength())) {
                    errors.add(String.format("Max length '%d' must be between %d and %d for prefix '%s'", r.getMaxLength(), range.getPrefixLength(), range.getType().getBitSize(), range));
                }
            } catch (Exception e) {
                errors.add("Prefix '" + r.getPrefix() + "' is invalid in (" + r + ")");
            }
        }
    }

    /**
     * Check for valid (non-missing, valid with regard to prefix and address family) maxLength.
     */
    @VisibleForTesting
    static boolean maxLengthIsValid(IpRange prefix, int maxLength) {
        return maxLength >= prefix.getPrefixLength() && maxLength <= prefix.getType().getBitSize();
    }

    public static List<String> toStringList(ImmutableResourceSet resources) {
        return resources.stream().map(Object::toString).toList();
    }

    /**
     * Invoke <code>runnable</code> and then <code>onError</code> if any exception occurred.
     */
    public static void cleanupOnError(Runnable runnable, Runnable onError) {
        try {
            runnable.run();
        } catch (Exception e) {
            try {
                onError.run();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    @NonNull
    static ResponseEntity<Object> badRequestError(Exception e) {
        return badRequestError(e.getMessage());
    }

    @NonNull
    static ResponseEntity<Object> badRequestError(String errorMessage) {
        return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
    }

    static List<BgpAnnouncement> getAffectedAnnouncements(RoaConfigurationData roaConfiguration,
                                                          Map<Boolean, Collection<BgpRisEntry>> announcements,
                                                          Set<AnnouncedRoute> ignoredAnnouncements,
                                                          Asn roaAsn, IpRange roaPrefix, Integer roaMaxLength) {

        final Set<AnnouncedRoute> routesValidatedByOthers = new HashSet<>();
        final NestedIntervalMap<IpResource, List<RoaConfigurationPrefixData>> currentRouteMap = allowedRoutesToNestedIntervalMap(roaConfiguration.getPrefixes());
        Stream.of(true, false)
                .flatMap(verifiedOrNot -> announcements.getOrDefault(verifiedOrNot, Collections.emptyList()).stream())
                .map(BgpRisEntry::toAnnouncedRoute)
                .forEach(announcedRoute -> {
                    final RouteValidityState currentValidityState = RouteOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                    if (currentValidityState == RouteValidityState.VALID &&
                            !(roaAsn.equals(announcedRoute.getOriginAsn()) && roaPrefix.equals(announcedRoute.getPrefix()))) {
                        routesValidatedByOthers.add(announcedRoute);
                    }
                });

        final List<BgpAnnouncement> bgpAnnouncements = makeBgpAnnouncementList(announcements, Collections.singletonList(
                new AllowedRoute(roaAsn, roaPrefix, roaMaxLength)),
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
        return knownAnnouncements;
    }
}
