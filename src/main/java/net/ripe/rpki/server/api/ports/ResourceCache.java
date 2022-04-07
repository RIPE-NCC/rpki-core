package net.ripe.rpki.server.api.ports;

import com.google.common.annotations.VisibleForTesting;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;

import java.util.Map;
import java.util.Optional;

public interface ResourceCache {

    boolean hasNoProductionResources();

    Optional<IpResourceSet> lookupResources(CaName user);

    DateTime lastUpdateTime();

    boolean hasNoMemberResources();

    void populateCache(Map<CaName, IpResourceSet> certifiableResources);

    Map<CaName, IpResourceSet> allMemberResources();

    default void verifyResourcesArePresent() {
        if (hasNoProductionResources()) {
            throw new IllegalStateException("Resource cache doesn't contain production CA resources");
        }
        if (hasNoMemberResources()) {
            throw new IllegalStateException("Resource cache doesn't contain member CA resources");
        }
    }
}
