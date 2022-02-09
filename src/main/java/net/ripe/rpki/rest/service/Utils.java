package net.ripe.rpki.rest.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.ROA;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Utils {
    static List<BgpAnnouncement> makeBgpAnnouncementList(Map<Boolean, Collection<BgpRisEntry>> announcements,
                                                         Iterable<? extends AllowedRoute> currentAllowedRoutes,
                                                         Set<AnnouncedRoute> ignoredAnnouncements) {
        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(currentAllowedRoutes);
        final RouteOriginValidationPolicy routeOriginValidationPolicy = new RouteOriginValidationPolicy();
        final List<BgpAnnouncement> result = new ArrayList<>();
        for (Boolean verifiedOrNot : new Boolean[]{true, false}) {
            if (announcements.containsKey(verifiedOrNot)) {
                for (BgpRisEntry announcement : announcements.get(verifiedOrNot)) {
                    final AnnouncedRoute announcedRoute = new AnnouncedRoute(announcement.getOrigin(), announcement.getPrefix());
                    final RouteValidityState currentValidityState = routeOriginValidationPolicy.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                    final boolean isSuppressed = ignoredAnnouncements.contains(announcedRoute);
                    result.add(new BgpAnnouncement(announcement.getOrigin().toString(), announcement.getPrefix().toString(), announcement.getVisibility(),
                            currentValidityState, isSuppressed, verifiedOrNot));
                }
            }
        }
        return result;
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

    public static Optional<String> errorsInUserInputRoas(final List<ROA> roas) {
        return errorsInUserInputRoas(roas.stream());
    }

    public static Optional<String> errorsInUserInputRoas(final ROA... roas) {
        return errorsInUserInputRoas(Stream.of(roas));
    }

    private static Optional<String> errorsInUserInputRoas(final Stream<ROA> roas) {
        final List<String> errors = new ArrayList<>();
        roas.forEach(r -> {
            if (r == null) {
                errors.add("ROA is null");
            } else {
                if (r.getAsn() == null) {
                    errors.add("ASN is empty in (" + r + ")");
                } else {
                    try {
                        Asn.parse(r.getAsn());
                    } catch (Exception e) {
                        errors.add("ASN '" + r.getAsn() + "' is invalid in (" + r + ")");
                    }
                }
                if (r.getPrefix() == null) {
                    errors.add("Prefix is empty in (" + r + ")");
                } else {
                    try {
                        IpRange.parse(r.getPrefix());

                        if (!lengthIsValid(r)) {
                            errors.add(String.format("Max length must be at most /32 (IPv4) or /128 (IPv6) but was %d", r.getMaxLength()));
                        }
                    } catch (Exception e) {
                        errors.add("Prefix '" + r.getPrefix() + "' is invalid in (" + r + ")");
                    }
                }
            }
        });
        return errors.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", errors));
    }

    public static <T> Set<T> toSet(Collection<T> c) {
        return c == null ? Collections.emptySet() : new HashSet<>(c);
    }

    /**
     * Check for valid (non-missing, valid with regard to prefix and address family) maxLength.
     */
    static boolean lengthIsValid(ROA roa) {
        IpRange range = IpRange.parse(roa.getPrefix());

        if (roa.getMaxLength() == null || !range.isLegalPrefix()) {
            return false;
        }

        return roa.getMaxLength() >= range.getPrefixLength() && roa.getMaxLength() <= range.getType().getBitSize();
    }
}
