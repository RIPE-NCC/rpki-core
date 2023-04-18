package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.ports.IanaRegistryXmlParser;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.Optional;

import static net.ripe.rpki.server.api.ports.IanaRegistryXmlParser.MajorityRir;

/**
 * RIPE NCC Specific implementation to find the RIPE NCC Production CA
 * and member CA resources.
 */
@Component
public class RipeNccResourceLookupService implements ResourceLookupService {

    private final IanaRegistryXmlParser ianaParser;
    private final ResourceCacheService resourceCacheService;

    @Inject
    public RipeNccResourceLookupService(IanaRegistryXmlParser ianaParser,
                                        @Lazy ResourceCacheService resourceCacheService) {
        this.ianaParser = ianaParser;
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public ImmutableResourceSet lookupProductionCaResources() {
        final ImmutableResourceSet ripeNccManagedSpace = resourceCacheService.getProductionCaResources().orElse(ImmutableResourceSet.empty());

        final ImmutableResourceSet.Builder productionCaResources = new ImmutableResourceSet.Builder(ripeNccManagedSpace);
        productionCaResources.addAll(ianaParser.getRirResources(MajorityRir.RIPE));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.AFRINIC, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.APNIC, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.ARIN, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.LACNIC, ripeNccManagedSpace));
        return productionCaResources.build();
    }

    @Override
    public Optional<ImmutableResourceSet> lookupProductionCaResourcesSet() {
        return resourceCacheService.getProductionCaResources();
    }

    private ImmutableResourceSet minoritySpaceForOtherRir(MajorityRir otherRir, ImmutableResourceSet ripeNccManagedSpace) {
        return ripeNccManagedSpace.intersection(ianaParser.getRirResources(otherRir));
    }

    @Override
    public ImmutableResourceSet lookupMemberCaPotentialResources(X500Principal caPrincipal) {
        final CaName caName = CaName.of(caPrincipal);
        if (resourceCacheService.getProductionCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the production CA (" + caName + ")");
        }
        if (resourceCacheService.getAllResourcesCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the all resources CA (" + caName + ")");
        }
        return resourceCacheService.getCaResources(caName).orElse(ImmutableResourceSet.empty());
    }

    @Override
    public Optional<ImmutableResourceSet> lookupIntermediateCaResources(X500Principal caName) {
        // Same as production CA for now, later we'll try to use INHERITED resources instead.
        return resourceCacheService.getProductionCaResources();
    }
}
