package net.ripe.rpki.rest.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.nimbusds.jose.util.Pair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.rest.pojo.BgpAnnouncement;
import net.ripe.rpki.rest.pojo.ROA;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.commons.validation.roa.RouteOriginValidationPolicy.allowedRoutesToNestedIntervalMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Utils {
    // current implementation in commons is stateless, however, likely not made static to be able to parameterize it
    // later.
    public static final RouteOriginValidationPolicy ROUTE_VALIDATION_POLICY = new RouteOriginValidationPolicy();

    static List<BgpAnnouncement> makeBgpAnnouncementList(Map<Boolean, Collection<BgpRisEntry>> announcements,
                                                         Iterable<? extends AllowedRoute> currentAllowedRoutes,
                                                         Set<AnnouncedRoute> ignoredAnnouncements) {
        final NestedIntervalMap<IpResource, List<AllowedRoute>> currentRouteMap = allowedRoutesToNestedIntervalMap(currentAllowedRoutes);

        // Verified announcements first, then the rest.
        return Stream.of(true, false)
            .flatMap(verifiedOrNot -> announcements.getOrDefault(verifiedOrNot, Collections.emptyList()).stream()
                .map(announcement -> {
                    // Create BgbAnnouncement, this needs the BgpRisEntry as well as the derived AnnouncedRoute
                    final AnnouncedRoute announcedRoute = announcement.toAnnouncedRoute();

                    final RouteValidityState currentValidityState = ROUTE_VALIDATION_POLICY.validateAnnouncedRoute(currentRouteMap, announcedRoute);
                    final boolean isSuppressed = ignoredAnnouncements.contains(announcedRoute);
                    return new BgpAnnouncement(
                        announcement.getOrigin().toString(), announcement.getPrefix().toString(),
                        announcement.getVisibility(), currentValidityState,
                        isSuppressed, verifiedOrNot);
                }))
            .collect(Collectors.toList());
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
                validateAsn(errors, r);
                validatePrefixAndMaxLength(errors, r);
            }
        });
        return errors.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", errors));
    }

    private static void validateAsn(List<String> errors, @NonNull ROA r) {
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

    private static void validatePrefixAndMaxLength(List<String> errors, @NonNull ROA r) {
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
        return resources.stream().map(Object::toString).collect(Collectors.toList());
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
    protected static ResponseEntity<Object> badRequestError(Exception e) {
        return badRequestError(e.getMessage());
    }

    @NonNull
    protected static ResponseEntity<Object> badRequestError(String errorMessage) {
        return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
    }

    static Optional<String> validateNoIdenticalROAs(RoaConfigurationData roaConfigurationData, List<ROA> newRoas, List<ROA> roasToDelete) {
        final List<ExistingROA> existingROAs = roaConfigurationData.getPrefixes().stream()
                .map(p -> new ExistingROA(p.getAsn(), p.getPrefix(), p.getNullableMaxLength()))
                .collect(Collectors.toList());
        return validateNoIdenticalROAs(existingROAs, newRoas, roasToDelete);
    }

    /**
     * Validate that there are not existing ROAs having the same AS and Prefix
     * but different Max Length fields.
     *
     * @return Optional error text for the first validation error that was found.
     */
    static Optional<String> validateNoIdenticalROAs(List<ExistingROA> existingROAs, List<ROA> newRoas, List<ROA> roasToDelete) {

        final Map<AnnouncedRoute, List<Integer>> newOnes = makeROAMap(newRoas);
        final Map<AnnouncedRoute, List<Integer>> deletedOnes = makeROAMap(roasToDelete);

        Optional<String> e = validateUniqueROAs("Error in new ROAs", newOnes);
        if (e.isPresent()) return e;

        e = validateUniqueROAs("Error in deleted ROAs", deletedOnes);
        if (e.isPresent()) return e;

        final Map<AnnouncedRoute, Optional<Integer>> newOnesUnique = uniqueEntries(newOnes);
        final Map<AnnouncedRoute, Optional<Integer>> deletedOnesUnique = uniqueEntries(deletedOnes);

        for (var existingRoa : existingROAs) {
            var key = new AnnouncedRoute(existingRoa.getAsn(), existingRoa.getPrefix());
            if (deletedOnesUnique.containsKey(key)) {
                var maxLengthToDelete = deletedOnesUnique.get(key).orElse(null);
                if (Objects.equals(existingRoa.getMaximumLength(), maxLengthToDelete)) {
                    newOnesUnique.remove(key);
                }
            }
        }

        for (var existingRoa : existingROAs) {
            var key = new AnnouncedRoute(existingRoa.getAsn(), existingRoa.getPrefix());
            if (newOnesUnique.containsKey(key)) {
                final Integer newMaxLength = newOnesUnique.get(key).orElse(null);
                if (!Objects.equals(existingRoa.getMaximumLength(), newMaxLength)) {
                    // we are not going to delete existing one
                    return Optional.of(getSameROAErrorMessage(existingRoa, key, newMaxLength));
                }
            }
        }
        return Optional.empty();
    }

    public static Map<AnnouncedRoute, Optional<Integer>> uniqueEntries(Map<AnnouncedRoute, List<Integer>> m) {
        var newM = new HashMap<AnnouncedRoute, Optional<Integer>>(m.size());
        m.forEach((k, list) -> newM.put(k, Optional.ofNullable(list.get(0))));
        return newM;
    }

    public static Optional<String> validateUniqueROAs(String prefix, Map<AnnouncedRoute, List<Integer>> newOnes) {
        for (var e : newOnes.entrySet()) {
            if (e.getValue().size() > 1) {
                return Optional.of(String.format("%s: there are more than one pair (%s, %s), max lengths: %s",
                        prefix, e.getKey().getOriginAsn(), e.getKey().getPrefix(), e.getValue()));
            }
        }
        return Optional.empty();
    }

    public static String getSameROAErrorMessage(Object existingRoa, AnnouncedRoute key, Integer newMaxLength) {
        return String.format(
                "There is an overlap in ROAs: existing %s has the same (ASN, prefix) as added %s",
                existingRoa,
                new ROA(key.getOriginAsn().toString(), key.getPrefix().toString(), newMaxLength));
    }

    public static Map<AnnouncedRoute, List<Integer>> makeROAMap(List<ROA> newRoas) {
        return newRoas.stream().map(r -> {
            AnnouncedRoute announcedRoute = new AnnouncedRoute(Asn.parse(r.getAsn()), IpRange.parse(r.getPrefix()));
            return Pair.of(announcedRoute, Collections.singletonList(r.getMaxLength()));
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight,
                (a, b) -> Streams.concat(a.stream(), b.stream())
                        .collect(Collectors.toList())));
    }

    @Value
    public static class ExistingROA {
        Asn asn;
        IpRange prefix;
        Integer maximumLength;

        @Override
        public String toString() {
            return "ROA{" +
                    "asn=" + asn +
                    ", prefix=" + prefix +
                    ", maximumLength=" + maximumLength +
                    '}';
        }
    }

}
