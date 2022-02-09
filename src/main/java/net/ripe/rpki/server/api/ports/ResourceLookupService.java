package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.IpResourceSet;

import javax.security.auth.x500.X500Principal;
import java.util.Optional;

public interface ResourceLookupService {

    IpResourceSet lookupProductionCaResources();

    Optional<IpResourceSet> lookupProductionCaResourcesSet();

    IpResourceSet lookupMemberCaPotentialResources(X500Principal caName);
}
