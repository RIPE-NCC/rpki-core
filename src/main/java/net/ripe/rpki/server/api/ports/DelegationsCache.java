package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.IpResourceSet;

import java.util.Optional;

public interface DelegationsCache {

    void cacheDelegations(IpResourceSet delegations);

    Optional<IpResourceSet> getDelegationsCache();
}
