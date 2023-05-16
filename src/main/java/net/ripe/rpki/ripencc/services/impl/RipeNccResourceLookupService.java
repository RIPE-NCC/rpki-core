package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.Optional;

/**
 * RIPE NCC Specific implementation to find the RIPE NCC Production CA
 * and member CA resources.
 */
@Component
public class RipeNccResourceLookupService implements ResourceLookupService {

    private final boolean useInheritedResourcesForIntermediateCa;
    private final ResourceCacheService resourceCacheService;

    @Inject
    public RipeNccResourceLookupService(
        @Value("${intermediate.ca.use.inherited.resources:false}") boolean useInheritedResourcesForIntermediateCa,
        @Lazy ResourceCacheService resourceCacheService
    ) {
        this.useInheritedResourcesForIntermediateCa = useInheritedResourcesForIntermediateCa;
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public Optional<ResourceExtension> lookupProductionCaResourcesSet() throws ResourceInformationNotAvailableException {
        ImmutableResourceSet resources = resourceCacheService.getProductionCaResources()
            .orElseThrow(() -> new ResourceInformationNotAvailableException("production CA resources not available"));
        return resources.isEmpty() ? Optional.empty() : Optional.of(ResourceExtension.ofResources(resources));
    }

    @Override
    public Optional<ResourceExtension> lookupIntermediateCaResourcesSet() throws ResourceInformationNotAvailableException {
        if (useInheritedResourcesForIntermediateCa) {
            return Optional.of(ResourceExtension.allInherited());
        } else {
            return lookupProductionCaResourcesSet();
        }
    }

    @Override
    public Optional<ResourceExtension> lookupMemberCaPotentialResources(X500Principal caPrincipal)
        throws ResourceInformationNotAvailableException
    {
        final CaName caName = CaName.of(caPrincipal);
        if (resourceCacheService.getProductionCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the production CA (" + caName + ")");
        }
        if (resourceCacheService.getAllResourcesCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the all resources CA (" + caName + ")");
        }
        ImmutableResourceSet resources = resourceCacheService.getCaResources(caName)
            .orElseThrow(() -> new ResourceInformationNotAvailableException("resource information for CA '" + caPrincipal.getName() + "' not available"));
        return resources.isEmpty() ? Optional.empty() : Optional.of(ResourceExtension.ofResources(resources));
    }

}
