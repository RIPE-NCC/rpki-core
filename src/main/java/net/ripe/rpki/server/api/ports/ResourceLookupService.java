package net.ripe.rpki.server.api.ports;

import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;

import javax.security.auth.x500.X500Principal;
import java.util.Optional;

public interface ResourceLookupService {

    /**
     * @return the certifiable resources of the production CA or empty when there are none.
     * @throws ResourceInformationNotAvailableException resource information is not available for this CA.
     */
    Optional<ResourceExtension> lookupProductionCaResourcesSet() throws ResourceInformationNotAvailableException;

    /**
     * Returns the resource extension to use for intermediate CA certificates if there are certifiable resources.
     *
     * <p>Normally an intermediate CA will have a copy of the production CA resources or will inherit all resources
     * of the production CA (based on a configuration flag).</p>
     *
     * @throws ResourceInformationNotAvailableException resource information is not available for this CA.
     */
    Optional<ResourceExtension> lookupIntermediateCaResourcesSet() throws ResourceInformationNotAvailableException;

    /**
     * @param caName the name of the member (hosted or non-hosted) CA.
     * @return the certifiable resources of the specified CA or empty when there are none.
     * @throws ResourceInformationNotAvailableException resource information is not available for this CA.
     */
    Optional<ResourceExtension> lookupMemberCaPotentialResources(X500Principal caName) throws ResourceInformationNotAvailableException;
}
