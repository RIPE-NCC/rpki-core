package net.ripe.rpki.bgpris;

import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.etree.IntervalMap;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class BgpRisEntryRepositoryBean implements BgpRisEntryViewService {

    private static final int VISIBILITY_THRESHOLD = 5;

    /*
     * All BgpRisEntries that have enough visibility.
     */
    private final AtomicReference<IntervalMap<IpRange, ArrayList<BgpRisEntry>>> entries = new AtomicReference<>(emptyEntries());

    @Override
    public boolean isEmpty() {
        return entries.get().isEmpty();
    }

    @Override
    public Collection<BgpRisEntry> findMostSpecificOverlapping(ImmutableResourceSet resources) {
        IntervalMap<IpRange, ArrayList<BgpRisEntry>> current = this.entries.get();

        Collection<BgpRisEntry> result = new HashSet<>();
        for (IpRange prefix : getPrefixes(resources)) {
            final List<BgpRisEntry> exactAndMoreSpecific = current.findExactAndAllMoreSpecific(prefix)
                    .stream()
                    .flatMap(Collection::stream).toList();
            result.addAll(exactAndMoreSpecific);

            final ImmutableResourceSet remaining = findResourcesNotCovered(prefix, exactAndMoreSpecific);
            addLessSpecificAnnouncements(current, result, remaining);
        }
        return result;
    }

    @Override
    public Map<Boolean, Collection<BgpRisEntry>> findMostSpecificContainedAndNotContained(ImmutableResourceSet resources) {
        IntervalMap<IpRange, ArrayList<BgpRisEntry>> current = this.entries.get();

        Collection<BgpRisEntry> containedEntries = new HashSet<>();
        Collection<BgpRisEntry> notContainedEntries = new HashSet<>();
        for (IpRange prefix : getPrefixes(resources)) {
            final List<BgpRisEntry> exactAndMoreSpecific = current.findExactAndAllMoreSpecific(prefix)
                    .stream()
                    .flatMap(Collection::stream).toList();
            containedEntries.addAll(exactAndMoreSpecific);
            final ImmutableResourceSet remaining = findResourcesNotCovered(prefix, exactAndMoreSpecific);
            addLessSpecificAnnouncements(current, notContainedEntries, remaining);
        }
        Map<Boolean, Collection<BgpRisEntry>> result = new HashMap<>();
        result.put(true, containedEntries);
        result.put(false, notContainedEntries);
        return result;
    }

    private void addLessSpecificAnnouncements(IntervalMap<IpRange, ArrayList<BgpRisEntry>> current, Collection<BgpRisEntry> result, ImmutableResourceSet remaining) {
        if (!remaining.isEmpty()) {
            getPrefixes(remaining).stream()
                    .map(current::findFirstLessSpecific)
                    .filter(Objects::nonNull)
                    .forEach(result::addAll);
        }
    }

    private ImmutableResourceSet findResourcesNotCovered(IpRange prefix, List<BgpRisEntry> exactAndMoreSpecific) {
        ImmutableResourceSet.Builder builder = new ImmutableResourceSet.Builder().add(prefix);
        for (BgpRisEntry entry: exactAndMoreSpecific) {
            builder.remove(entry.getPrefix());
        }
        return builder.build();
    }

    @Override
    public void resetEntries(Collection<BgpRisEntry> entries) {
        IntervalMap<IpRange, ArrayList<BgpRisEntry>> copy = emptyEntries();
        for (BgpRisEntry entry : entries) {
            if (keepEntry(entry)) {
                ArrayList<BgpRisEntry> exact = copy.findExact(entry.getPrefix());
                if (exact == null) {
                    exact = new ArrayList<>();
                    copy.put(entry.getPrefix(), exact);
                }
                if (!exact.contains(entry)) {
                    exact.add(entry);
                }
            }
        }
        this.entries.set(copy);
    }

    private boolean keepEntry(BgpRisEntry entry) {
        return meetsVisibilityThreshold(entry) && !isLargePrefixes(entry.getPrefix());
    }

    private boolean meetsVisibilityThreshold(BgpRisEntry entry) {
        return entry.getVisibility() >= VISIBILITY_THRESHOLD;
    }

    private boolean isLargePrefixes(IpRange prefix) {
        switch (prefix.getType()) {
        case ASN:
            return false;
        case IPv4:
            return prefix.getPrefixLength() < 8;
        case IPv6:
            return prefix.getPrefixLength() < 12;
        }
        throw new IllegalArgumentException("Resource of unknown type: " + prefix);
    }

    private static List<IpRange> getPrefixes(final ImmutableResourceSet resources) {
        List<IpRange> result = new ArrayList<>();
        for (IpResource resource : resources) {
            if (resource instanceof IpRange range) {
                result.addAll(range.splitToPrefixes());
            } else if (resource instanceof IpAddress) {
                result.add(IpRange.range((IpAddress) resource, (IpAddress) resource));
            }
        }
        return result;
    }

    private NestedIntervalMap<IpRange, ArrayList<BgpRisEntry>> emptyEntries() {
        return new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
    }
}
