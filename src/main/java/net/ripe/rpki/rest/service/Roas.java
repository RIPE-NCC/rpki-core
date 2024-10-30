package net.ripe.rpki.rest.service;

import com.google.common.collect.Streams;
import lombok.Value;
import lombok.experimental.UtilityClass;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RoaPrefixData;
import net.ripe.rpki.rest.pojo.PublishSet;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;

import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class Roas {
    // FIXME: This is sketchy: prefix is only used in error message. Why is the parameter required?
    public static Optional<String> validateUniqueROAs(String prefix, Map<AnnouncedRoute, List<Integer>> newOnes) {
        for (var e : newOnes.entrySet()) {
            var maxLengths = e.getValue();
            if (maxLengths.size() > 1) {
                var sorted = maxLengths.stream().sorted().toList();
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

    private static AllowedRoute toAllowedRoute(ApiRoaPrefix roa) {
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

    public static <T extends RoaPrefixData> Optional<String> validateRoaUpdate(Collection<T> futureRoutes) {
        var futureMap = futureRoutes.stream().collect(Collectors.toMap(
                r -> new AnnouncedRoute(r.getAsn(), r.getPrefix()),
                r -> Collections.singletonList(r.getMaximumLength()),
                (a, b) -> Streams.concat(a.stream(), b.stream()).toList()));

        return validateUniqueROAs("Error in future ROAs", futureMap);
    }

    @Value
    public static class RoaDiff {
        Set<AllowedRoute> added;
        Set<AllowedRoute> deleted;
    }
}
