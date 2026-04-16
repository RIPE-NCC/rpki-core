package net.ripe.rpki.rest.service;

import com.google.common.collect.Sets;
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
    public static RoaDiff toDiff(PublishSet publishSet) {
        var additions = publishSet.getAdded().stream().map(Roas::toAllowedRoute).collect(Collectors.toSet());
        var deletions = publishSet.getDeleted().stream().map(Roas::toAllowedRoute).collect(Collectors.toSet());
        return new RoaDiff(
                Sets.difference(additions, deletions),
                Sets.difference(deletions, additions));
    }

    static AllowedRoute toAllowedRoute(ApiRoaPrefix roa) {
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
        var futureMap = futureRoutes.stream().collect(
                Collectors.toMap(
                        r -> new AnnouncedRoute(r.getAsn(), r.getPrefix()),
                        r -> Collections.singletonList(r.getMaximumLength()),
                        (a, b) -> Streams.concat(a.stream(), b.stream()).toList()));

        for (var e : futureMap.entrySet()) {
            var maxLengths = e.getValue();
            if (maxLengths.size() > 1) {
                var sorted = maxLengths.stream().sorted().toList();
                return Optional.of(String.format("%s: there are more than one pair (%s, %s), max lengths: %s",
                        "Error in future ROAs", e.getKey().getOriginAsn(), e.getKey().getPrefix(), sorted));
            }
        }
        return Optional.empty();
    }

    @Value
    public static class RoaDiff {
        Set<AllowedRoute> added;
        Set<AllowedRoute> deleted;
    }
}
