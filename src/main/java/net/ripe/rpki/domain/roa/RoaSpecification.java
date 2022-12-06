package net.ripe.rpki.domain.roa;

import com.google.common.base.Preconditions;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.Roa;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.util.Specification;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import org.joda.time.Instant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoaSpecification implements Specification<Roa> {
    private final Asn asn;
    private final ValidityPeriod validityPeriod;
    private final Map<IpRange, Integer> prefixes = new HashMap<>();

    public RoaSpecification(Asn asn, ValidityPeriod validityPeriod) {
        this.asn = asn;
        this.validityPeriod = validityPeriod;
    }

    public void putPrefix(IpRange prefix, int maximumLength) {
        prefixes.put(prefix, maximumLength);
    }

    public void removePrefix(IpRange prefix) {
        prefixes.remove(prefix);
    }

    public int getPrefix(IpRange prefix) {
        return prefixes.get(prefix);
    }

    public boolean containsPrefix(IpRange prefix) {
        return prefixes.containsKey(prefix);
    }


    public void addAllowedRoute(AllowedRoute allowedRoute) {
        Preconditions.checkArgument(asn.equals(allowedRoute.getAsn()), "asn mismatch", allowedRoute);
        putPrefix(allowedRoute.getPrefix(), allowedRoute.getMaximumLength());
    }

    public void removeAllowedRoute(AllowedRoute allowedRoute) {
        Preconditions.checkArgument(asn.equals(allowedRoute.getAsn()), "asn mismatch", allowedRoute);
        removePrefix(allowedRoute.getPrefix());
    }

    @Override
    public boolean isSatisfiedBy(Roa roa) {
        return checkValidityPeriod(roa) && asn.equals(roa.getAsn()) && checkPrefixes(roa);
    }

    private boolean checkValidityPeriod(Roa roa) {
        ValidityPeriod vp = calculateValidityPeriod();
        return vp != null
                && !roa.getValidityPeriod().getNotValidBefore().isAfterNow()
                && vp.getNotValidAfter().equals(roa.getValidityPeriod().getNotValidAfter());
    }

    private boolean checkPrefixes(Roa roa) {
        if (prefixes.size() != roa.getPrefixes().size()) {
            return false;
        }
        ImmutableResourceSet resources = getNormalisedResources();
        for (RoaPrefix actual: roa.getPrefixes()) {
            if (!(resources.contains(actual.getPrefix())
                    && containsPrefix(actual.getPrefix())
                    && getPrefix(actual.getPrefix()) == actual.getEffectiveMaximumLength())) {
                return false;
            }
        }
        return true;
    }

    public boolean allows(Roa roa) {
        ValidityPeriod vp = calculateValidityPeriod();
        if (vp == null || vp.getNotValidAfter().isBefore(roa.getValidityPeriod().getNotValidAfter())) {
            return false;
        }
        if (!roa.getAsn().equals(asn)) {
            return false;
        }
        for (RoaPrefix actual: roa.getPrefixes()) {
            if (!isPrefixAllowed(actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPrefixAllowed(RoaPrefix actual) {
        for (IpRange prefix: prefixes.keySet()) {
            if (prefix.contains(actual.getPrefix()) && getPrefix(prefix) == actual.getEffectiveMaximumLength()) {
                return true;
            }
        }
        return false;
    }

    public Asn getAsn() {
        return asn;
    }

    public ImmutableResourceSet getNormalisedResources() {
        return ImmutableResourceSet.of(prefixes.keySet());
    }

    public ValidityPeriod calculateValidityPeriod() {
        Instant now = new Instant();
        return validityPeriod.isExpiredAt(now) ? null : validityPeriod.withNotValidBefore(now);
    }

    public List<RoaPrefix> calculatePrefixes() {
        List<RoaPrefix> result = new ArrayList<>(prefixes.size());
        for (Map.Entry<IpRange, Integer> prefix: prefixes.entrySet()) {
            result.add(new RoaPrefix(prefix.getKey(), prefix.getValue()));
        }
        return result;
    }

    public boolean hasResources() {
        return !getNormalisedResources().isEmpty();
    }
}
