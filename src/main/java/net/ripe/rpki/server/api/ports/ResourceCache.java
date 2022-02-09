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

    void verifyResourcesArePresent();

    boolean hasNoMemberResources();

    void populateCache(Map<CaName, IpResourceSet> certifiableResources);

    Map<CaName, IpResourceSet> allMemberResources();

    @VisibleForTesting
    void updateEntry(CaName caName, IpResourceSet parse);
}
