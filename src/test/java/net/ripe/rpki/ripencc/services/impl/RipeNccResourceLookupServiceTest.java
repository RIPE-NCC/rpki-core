package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RipeNccResourceLookupServiceTest {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=DELEGATIONS");
    private static final X500Principal ALL_RESOURCES_CA_NAME= new X500Principal("CN=ALL_RESOURCES");

    private ImmutableResourceSet managedResources = ImmutableResourceSet.parse("2/8, 3/24, 4/24, 2003::/32");


    private RipeNccResourceLookupService subject;

    private ResourceCacheService resourceCacheService;

    @Before
    public void setUp() {
        resourceCacheService = mock(ResourceCacheService.class);

        subject = new RipeNccResourceLookupService(true, resourceCacheService);

        when(resourceCacheService.getProductionCaName()).thenReturn(CaName.of(PRODUCTION_CA_NAME));
        when(resourceCacheService.getAllResourcesCaName()).thenReturn(CaName.of(ALL_RESOURCES_CA_NAME));
        when(resourceCacheService.getProductionCaResources()).thenReturn(Optional.of(managedResources));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void should_not_lookup_resources_of_production_ca() throws ResourceInformationNotAvailableException {
        subject.lookupMemberCaPotentialResources(PRODUCTION_CA_NAME);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void should_not_lookup_resources_of_all_resources_ca() throws ResourceInformationNotAvailableException {
        subject.lookupMemberCaPotentialResources(ALL_RESOURCES_CA_NAME);
    }

    @Test
    public void should_extract_membership_id_from_member_ca_and_query_member_resources() throws ResourceInformationNotAvailableException {
        when(resourceCacheService.getCaResources(any(CaName.class))).thenReturn(Optional.of(ImmutableResourceSet.parse("2.0.0.0/16, 2003::/32")));

        Optional<ResourceExtension> resources = subject.lookupMemberCaPotentialResources(new X500Principal("CN=1"));

        assertThat(resources).hasValue(ResourceExtension.ofResources(ImmutableResourceSet.parse("2.0.0.0/16, 2003::/32")));
    }

    @Test
    public void should_use_inherited_resources_for_intermediate_cas() throws ResourceInformationNotAvailableException {
        subject = new RipeNccResourceLookupService(true, resourceCacheService);
        assertThat(subject.lookupIntermediateCaResourcesSet()).hasValue(ResourceExtension.allInherited());
    }

    @Test
    public void should_use_production_resources_for_intermediate_cas() throws ResourceInformationNotAvailableException {
        subject = new RipeNccResourceLookupService(false, resourceCacheService);
        assertThat(subject.lookupIntermediateCaResourcesSet()).hasValue(ResourceExtension.ofResources(managedResources));
    }
}
