package net.ripe.rpki.services.impl.background;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.util.HashMap;
import java.util.Map;

import static net.ripe.rpki.services.impl.background.ResourceCacheService.resourcesDiff;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheServiceTest {
    @Mock
    private ResourceCache resourceCache;

    @Mock
    private DelegationsCache delegationsCache;

    @Mock
    private ResourceServicesClient resourceServicesClient;

    @Mock
    private RoaConfigUpdater roaConfigUpdater;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ResourceCacheService subject;

    @Before
    public void setUp() {
        subject = new ResourceCacheService(roaConfigUpdater, transactionTemplate, resourceServicesClient, resourceCache, delegationsCache,
            new X500Principal("CN=666"), new X500Principal("CN=123"), new SimpleMeterRegistry());
    }

    @Test
    public void shouldFailIfInternetResourceServicesIsNotAvailable() throws Exception {
        given(resourceServicesClient.fetchAllMemberResources()).willReturn(null);
        subject.updateMembersCache();
        verify(resourceCache, never()).populateCache(null);
    }

    @Test
    @Ignore
    public void shouldUpdateResourceCache() {
        ImmutableMap<CaName, IpResourceSet> certifiableResources = ImmutableMap.of(CaName.of(1L), IpResourceSet.parse("10.0.0.0/8,AS59946"));

        ResourceServicesClient.MemberResources memberResources = mock(ResourceServicesClient.MemberResources.class);
        given(memberResources.getCertifiableResources()).willReturn(certifiableResources);
        given(resourceServicesClient.fetchAllMemberResources()).willReturn(memberResources);

        subject.updateMembersCache();

        ArgumentCaptor<TransactionCallbackWithoutResult> captor =
            ArgumentCaptor.forClass(TransactionCallbackWithoutResult.class);
        verify(transactionTemplate).execute(captor.capture());
    }



    @Test
    public void shouldCalculateProperDiff() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));
        registryResources.put(CaName.of(2), IpResourceSet.parse("20.20.0.0/16, AS123"));
        registryResources.put(CaName.of(4), IpResourceSet.parse("30.0.0.0/8, 32.0.0.0/8"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.of(1), IpResourceSet.parse("12.0.0.0/8, 13.0.0.0/8, 14.0.0.0/8")); //This counts as 1 resource only (12.0.0.0 - 14.255.255.255)
        localResources.put(CaName.of(3), IpResourceSet.parse("19.0.0.0/8, 21.0.0.0/8, AS16"));
        localResources.put(CaName.of(4), IpResourceSet.parse("32.0.0.0/8, 34.0.0.0/8, 36.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(3, resourceDiff.getLocalSize());
        assertEquals(3, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.of(1));
        assertEquals(1, changes1.getAdded());
        assertEquals(1, changes1.getDeleted());

        final ResourceCacheService.Changes changes2 = resourceDiff.getChangesMap().get(CaName.of(2));
        assertEquals(2, changes2.getAdded());
        assertEquals(0, changes2.getDeleted());

        final ResourceCacheService.Changes changes3 = resourceDiff.getChangesMap().get(CaName.of(3));
        assertEquals(0, changes3.getAdded());
        assertEquals(3, changes3.getDeleted());

        final ResourceCacheService.Changes changes4 = resourceDiff.getChangesMap().get(CaName.of(4));
        assertEquals(1, changes4.getAdded());
        assertEquals(2, changes4.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExact() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(1, resourceDiff.getLocalSize());
        assertEquals(1, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.of(1));
        assertEquals(2, changes1.getAdded());
        assertEquals(0, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactTheOtherWay() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(1, resourceDiff.getLocalSize());
        assertEquals(1, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.of(1));
        assertEquals(0, changes1.getAdded());
        assertEquals(2, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactReverse() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.of(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(1, resourceDiff.getLocalSize());
        assertEquals(1, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.of(1));
        assertEquals(0, changes1.getAdded());
        assertEquals(2, changes1.getDeleted());
    }

    @Test
    public void shouldCountDelegations() {
        final IpResourceSet registry = IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8, 20.20.0.0/16, AS123");
        final IpResourceSet local    = IpResourceSet.parse("12.0.0.0/8, 21.0.0.0/8, AS16");

        ResourceCacheService.DelegationDiffStat resourceDiff = ResourceCacheService.delegationsDiff(registry, local);
        assertEquals(3, resourceDiff.getLocalResourceCount());
        assertEquals(4, resourceDiff.getRegistrySizeResourceCount());
        assertEquals(3, resourceDiff.getTotalAdded());
        assertEquals(2, resourceDiff.getTotalDeleted());
    }
}