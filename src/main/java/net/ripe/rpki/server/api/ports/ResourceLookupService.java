package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.ImmutableResourceSet;

import javax.security.auth.x500.X500Principal;
import java.util.Optional;

public interface ResourceLookupService {

    ImmutableResourceSet lookupProductionCaResources();

    Optional<ImmutableResourceSet> lookupProductionCaResourcesSet();

    ImmutableResourceSet lookupMemberCaPotentialResources(X500Principal caName);
}
