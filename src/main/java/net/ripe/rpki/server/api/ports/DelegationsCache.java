package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.ImmutableResourceSet;

import java.util.Optional;

public interface DelegationsCache {

    void cacheDelegations(ImmutableResourceSet delegations);

    Optional<ImmutableResourceSet> getDelegationsCache();
}
