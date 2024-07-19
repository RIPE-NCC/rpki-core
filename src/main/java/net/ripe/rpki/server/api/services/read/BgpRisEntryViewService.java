package net.ripe.rpki.server.api.services.read;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.dto.BgpRisEntry;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public interface BgpRisEntryViewService {

    boolean isEmpty();

    /**
     * @return all matching BGP RIS entries that do not have a more specific matching entry.
     * <emph>Take care when using this method: it does not consistently return a covering less specific.</emph>
     * TODO: Clarify the behaviour of this method.
     */
    Collection<BgpRisEntry> findMostSpecificOverlapping(ImmutableResourceSet resources);

    /**
     * findMostSpecificContainedAndNotContained
     *
     * @param resources
     * @return all matching BGP RIS entries that do not have a more specific matching entry,
     * split into two collections: those fully covered (contained) by the given resource set
     * and those not fully covered by the given set.
     */
    Map<Boolean, Collection<BgpRisEntry>> findMostSpecificContainedAndNotContained(ImmutableResourceSet resources);

    void resetEntries(Collection<BgpRisEntry> entries);

    Instant getLastUpdated();

    void setLastUpdated(Instant lastUpdated);
}
