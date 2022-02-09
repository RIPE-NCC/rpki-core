package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.IpResourceSet;
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
    public IpResourceSet lookupProductionCaResources() {
        final IpResourceSet ripeNccManagedSpace = resourceCacheService.getProductionCaResources().orElse(new IpResourceSet());

        final IpResourceSet productionCaResources = new IpResourceSet(ripeNccManagedSpace);
        productionCaResources.addAll(ianaParser.getRirResources(MajorityRir.RIPE));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.AFRINIC, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.APNIC, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.ARIN, ripeNccManagedSpace));
        productionCaResources.addAll(minoritySpaceForOtherRir(MajorityRir.LACNIC, ripeNccManagedSpace));
        return productionCaResources;
    }

    @Override
    public Optional<IpResourceSet> lookupProductionCaResourcesSet() {
        return resourceCacheService.getProductionCaResources();
    }

    private IpResourceSet minoritySpaceForOtherRir(MajorityRir otherRir, IpResourceSet ripeNccManagedSpace) {
        IpResourceSet fromOtherRir = new IpResourceSet(ripeNccManagedSpace);
        fromOtherRir.retainAll((ianaParser.getRirResources(otherRir)));
        return fromOtherRir;
    }

    @Override
    public IpResourceSet lookupMemberCaPotentialResources(X500Principal caPrincipal) {
        final CaName caName = CaName.of(caPrincipal);
        if (resourceCacheService.getProductionCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the production CA (" + caName + ")");
        }
        if (resourceCacheService.getAllResourcesCaName().equals(caName)) {
            throw new UnsupportedOperationException("This method does not support resource lookup for the all resources CA (" + caName + ")");
        }
        return resourceCacheService.getCaResources(caName).orElse(new IpResourceSet());
    }
}
