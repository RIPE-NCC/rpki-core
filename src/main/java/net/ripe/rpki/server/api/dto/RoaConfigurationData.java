package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;

public class RoaConfigurationData extends ValueObjectSupport {

    private final List<RoaConfigurationPrefixData> prefixes;

    public RoaConfigurationData(List<RoaConfigurationPrefixData> prefixes) {
        this.prefixes = checkNotNull(prefixes, "prefixes is required");
    }

    public List<RoaConfigurationPrefixData> getPrefixes() {
        return Collections.unmodifiableList(prefixes);
    }

    public List<AllowedRoute> toAllowedRoutes() {
        return toAllowedRoutes(prefixes);
    }

    private static List<AllowedRoute> toAllowedRoutes(Collection<? extends RoaConfigurationPrefixData> prefixes) {
        return prefixes.stream()
                .map(roaPrefix -> new AllowedRoute(roaPrefix.getAsn(), roaPrefix.getPrefix(), roaPrefix.getMaximumLength()))
                .collect(Collectors.toList());
    }
}
