package net.ripe.rpki.server.api.dto;

import lombok.NonNull;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import net.ripe.rpki.util.Streams;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoaConfigurationData extends ValueObjectSupport {

    private final List<RoaConfigurationPrefixData> prefixes;

    public RoaConfigurationData(@NonNull List<RoaConfigurationPrefixData> prefixes) {
        this.prefixes = new ArrayList<>(prefixes);
        this.prefixes.sort(RoaConfigurationPrefixData.COMPARATOR);
    }

    public List<RoaConfigurationPrefixData> getPrefixes() {
        return Collections.unmodifiableList(prefixes);
    }

    public List<AllowedRoute> toAllowedRoutes() {
        return toAllowedRoutes(prefixes);
    }

    public String entityTag() {
        return Streams.entityTag(
            prefixes.stream().flatMap(prefix -> Stream.of(
                stringBytes(prefix.getAsn()),
                stringBytes(prefix.getRoaPrefix().getPrefix()),
                stringBytes(prefix.getRoaPrefix().getMaximumLength())
            ))
        );
    }

    private static List<AllowedRoute> toAllowedRoutes(Collection<? extends RoaConfigurationPrefixData> prefixes) {
        return prefixes.stream()
                .map(roaPrefix -> new AllowedRoute(roaPrefix.getAsn(), roaPrefix.getPrefix(), roaPrefix.getMaximumLength()))
                .collect(Collectors.toList());
    }

    private static byte[] stringBytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }
}
