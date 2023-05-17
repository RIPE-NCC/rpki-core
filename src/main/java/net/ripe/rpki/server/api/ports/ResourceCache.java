package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;

import java.util.Map;
import java.util.Optional;

public interface ResourceCache {

    boolean hasNoProductionResources();

    /**
     * Looks up the resources of a RIPE NCC member. {@link Optional#empty()} is returned when the resource cache is
     * not populated (determined by checking if there are production CA resources in the cache). Otherwise the resources
     * of the member are returned, which can be the empty resource set if the member currently has no certifiable
     * resources.
     */
    Optional<ImmutableResourceSet> lookupResources(CaName member);

    DateTime lastUpdateTime();

    boolean hasNoMemberResources();

    void populateCache(Map<CaName, ImmutableResourceSet> certifiableResources);

    Map<CaName, ImmutableResourceSet> allMemberResources();

    default void verifyResourcesArePresent() {
        if (hasNoProductionResources()) {
            throw new IllegalStateException("Resource cache doesn't contain production CA resources");
        }
        if (hasNoMemberResources()) {
            throw new IllegalStateException("Resource cache doesn't contain member CA resources");
        }
    }
}
