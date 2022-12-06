package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.ports.IanaRegistryXmlParser;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RipeNccResourceLookupServiceTest {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=DELEGATIONS");
    private static final X500Principal ALL_RESOURCES_CA_NAME= new X500Principal("CN=ALL_RESOURCES");

    private ImmutableResourceSet managedResources = ImmutableResourceSet.parse("2/8, 3/24, 4/24, 2003::/32");
    private ImmutableResourceSet ncc = ImmutableResourceSet.parse("2/8, 2003::/32");
    private ImmutableResourceSet afrinic = ImmutableResourceSet.parse("3/8");
    private ImmutableResourceSet apnic = ImmutableResourceSet.parse("4/8");
    private ImmutableResourceSet fromApnic = ImmutableResourceSet.parse("4/24");
    private ImmutableResourceSet arin = ImmutableResourceSet.parse("5/8");
    private ImmutableResourceSet lacnic = ImmutableResourceSet.parse("6/8");
    private ImmutableResourceSet fromAfrinic = ImmutableResourceSet.parse("3/24");


    private RipeNccResourceLookupService subject;

    private ResourceCacheService resourceCacheService;
    private IanaRegistryXmlParser ianaRegistryXmlParser;

    @Before
    public void setUp() {
        resourceCacheService = mock(ResourceCacheService.class);
        ianaRegistryXmlParser = mock(IanaRegistryXmlParser.class);

        subject = new RipeNccResourceLookupService(ianaRegistryXmlParser, resourceCacheService);

        when(resourceCacheService.getProductionCaName()).thenReturn(CaName.of(PRODUCTION_CA_NAME));
        when(resourceCacheService.getAllResourcesCaName()).thenReturn(CaName.of(ALL_RESOURCES_CA_NAME));
        when(resourceCacheService.getProductionCaResources()).thenReturn(Optional.of(managedResources));
        when(ianaRegistryXmlParser.getRirResources(IanaRegistryXmlParser.MajorityRir.RIPE)).thenReturn(ncc);
        when(ianaRegistryXmlParser.getRirResources(IanaRegistryXmlParser.MajorityRir.AFRINIC)).thenReturn(afrinic);
        when(ianaRegistryXmlParser.getRirResources(IanaRegistryXmlParser.MajorityRir.ARIN)).thenReturn(arin);
        when(ianaRegistryXmlParser.getRirResources(IanaRegistryXmlParser.MajorityRir.APNIC)).thenReturn(apnic);
        when(ianaRegistryXmlParser.getRirResources(IanaRegistryXmlParser.MajorityRir.LACNIC)).thenReturn(lacnic);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void should_not_lookup_resources_of_production_ca() {
        subject.lookupMemberCaPotentialResources(PRODUCTION_CA_NAME);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void should_not_lookup_resources_of_all_resources_ca() {
        subject.lookupMemberCaPotentialResources(ALL_RESOURCES_CA_NAME);
    }

//    @Test
//    public void should_get_ripe_ncc_and_minority_space() {
//        ResourceClassMap expected = ResourceClassMap.empty()
//            .plus("RIPE", ncc)
//            .plus("AFRINIC", fromAfrinic)
//            .plus("APNIC", fromApnic)
//            .plus("ARIN", new ImmutableResourceSet())
//            .plus("LACNIC", new ImmutableResourceSet());
//        assertEquals(Optional.of(expected), subject.lookupProductionCaResources());
//    }

    @Test
    public void should_extract_membership_id_from_member_ca_and_query_member_resources() {
        when(resourceCacheService.getCaResources(any(CaName.class))).thenReturn(Optional.of(ImmutableResourceSet.parse("2.0.0.0/16, 2003::/32")));

        ImmutableResourceSet resources = subject.lookupMemberCaPotentialResources(new X500Principal("CN=1"));

        assertEquals(ImmutableResourceSet.parse("2.0.0.0/16, 2003::/32"), resources);
    }
}
