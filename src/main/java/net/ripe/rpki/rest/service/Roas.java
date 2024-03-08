package net.ripe.rpki.rest.service;

import com.google.common.collect.Streams;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.rest.pojo.PublishSet;
import net.ripe.rpki.rest.pojo.ROA;

import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Roas {

    public static Optional<String> validateUniqueROAs(String prefix, Map<AnnouncedRoute, List<Integer>> newOnes) {
        for (var e : newOnes.entrySet()) {
            var maxLengths = e.getValue();
            if (maxLengths.size() > 1) {
                var sorted = maxLengths.stream().sorted().collect(Collectors.toList());
                return Optional.of(String.format("%s: there are more than one pair (%s, %s), max lengths: %s",
                        prefix, e.getKey().getOriginAsn(), e.getKey().getPrefix(), sorted));
            }
        }
        return Optional.empty();
    }

    public static RoaDiff toDiff(PublishSet publishSet) {
        return new RoaDiff(
                publishSet.getAdded().stream().map(Roas::toAllowedRoute).collect(Collectors.toSet()),
                publishSet.getDeleted().stream().map(Roas::toAllowedRoute).collect(Collectors.toSet())
        );
    }

    private static AllowedRoute toAllowedRoute(ROA roa) {
        var roaIpRange = IpRange.parse(roa.getPrefix());
        var maxLength = roa.getMaxLength() != null ? roa.getMaxLength() : roaIpRange.getPrefixLength();
        return new AllowedRoute(Asn.parse(roa.getAsn()), roaIpRange, maxLength);
    }

    public static Set<AllowedRoute> applyDiff(Set<AllowedRoute> currentRoas, RoaDiff diff) {
        var futureRoas = new HashSet<>(currentRoas);
        diff.getDeleted().forEach(futureRoas::remove);
        futureRoas.addAll(diff.getAdded());
        return futureRoas;
    }

    public static Optional<String> validateRoaUpdate(Set<AllowedRoute> futureRoutes) {
        var futureMap = futureRoutes.stream().collect(Collectors.toMap(
                r -> new AnnouncedRoute(r.getAsn(), r.getPrefix()),
                r -> Collections.singletonList(r.getMaximumLength()),
                (a, b) -> Streams.concat(a.stream(), b.stream()).collect(Collectors.toList())));

        return validateUniqueROAs("Error in future ROAs", futureMap);
    }

    @Value
    public static class RoaDiff {
        Set<AllowedRoute> added;
        Set<AllowedRoute> deleted;
    }
}
