package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.AsnResource;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.Ipv4Allocation;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.MemberResources;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.RipeNccDelegation;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.RipeNccDelegations;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.TotalResources;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static net.ripe.rpki.services.impl.background.ResourceCacheService.resourcesDiff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheServiceTest {
    private final TransactionOperationsSpy transactionTemplate = new TransactionOperationsSpy();

    private final ResourceCache resourceCache = new InMemoryResourceCache(CaName.parse("CN=RIPE NCC Resources,O=RIPE NCC,C=NL"));
    private final DelegationsCache delegationsCache = new InMemoryDelegationsCache();

    @Mock
    private ResourceServicesClient resourceServicesClient;

    private ResourceCacheService subject;

    @Before
    public void setUp() {
        transactionTemplate.reset();
        subject = new ResourceCacheService(transactionTemplate, resourceServicesClient, resourceCache, delegationsCache,
            new X500Principal("CN=666"), new X500Principal("CN=123"), false, new SimpleMeterRegistry());
    }

    @Test
    public void shouldUpdateDelegations() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());
        subject.updateFullResourceCache();
        assertThat(delegationsCache.getDelegationsCache()).isPresent();
        assertThat(transactionTemplate.getCommits()).isOne();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldUpdateResourceCache() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());
        subject.updateFullResourceCache();
        assertThat(resourceCache.allMemberResources()).isNotEmpty();
        assertThat(transactionTemplate.getCommits()).isOne();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }


    @Test
    public void shouldAcceptEmptyUpdate() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());
        subject.updateFullResourceCache();

        subject.updateFullResourceCache();

        assertThat(resourceCache.allMemberResources()).isNotEmpty();
        assertThat(transactionTemplate.getCommits()).isEqualTo(2);
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldRollbackOnDelegationsCacheRejection() {
        resourceCache.populateCache(Collections.emptyMap()); // empty cache allows for any update
        delegationsCache.cacheDelegations(DataSamples.productionCaDelegations(DataSamples.memberResources()));
        DateTime lastUpdate = resourceCache.lastUpdateTime();
        subject.updateFullResourceCache();
        assertThat(delegationsCache.getDelegationsCache()).hasValue(DataSamples.productionCaDelegations(DataSamples.memberResources()));
        assertThat(resourceCache.allMemberResources()).isEmpty();
        assertThat(resourceCache.lastUpdateTime()).isEqualTo(lastUpdate);
        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isOne();
    }

    @Test
    public void shouldRollbackOnMemberResourceCacheRejection() {
        resourceCache.populateCache(DataSamples.memberResources().getCertifiableResources());
        delegationsCache.cacheDelegations(null); // empty cache allows for any update
        DateTime lastUpdate = resourceCache.lastUpdateTime();
        subject.updateFullResourceCache();
        assertThat(delegationsCache.getDelegationsCache()).isEmpty();
        assertThat(resourceCache.allMemberResources()).isEqualTo(DataSamples.memberResources().getCertifiableResources());
        assertThat(resourceCache.lastUpdateTime()).isEqualTo(lastUpdate);
        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isOne();
    }

    @Test
    public void shouldAllowOneTimeOverrideOfRejections() {
        final MemberResources memberResources = DataSamples.memberResources();
        final RipeNccDelegations ripeNccDelegations = DataSamples.ripeNccDelegations(memberResources);

        resourceCache.populateCache(memberResources.getCertifiableResources());
        delegationsCache.cacheDelegations(ripeNccDelegations.allDelegationResources());
        DateTime lastUpdate = resourceCache.lastUpdateTime();

        final MemberResources resourcesToReject = DataSamples.rejectedMemberResources();
        when(resourceServicesClient.fetchAllResources()).thenReturn(
            new TotalResources(resourcesToReject, DataSamples.ripeNccDelegations(resourcesToReject)));

        subject = new ResourceCacheService(transactionTemplate, resourceServicesClient, resourceCache, delegationsCache,
                new X500Principal("CN=666"), new X500Principal("CN=123"), true, new SimpleMeterRegistry());
        // override is set
        assertThat(subject.isAcceptOneRejectedResourceCacheUpdate()).isTrue();
        subject.updateFullResourceCache();
        // The one time override was "used"
        assertThat(subject.isAcceptOneRejectedResourceCacheUpdate()).isFalse();

        final IpResourceSet expectedValue = DataSamples.ripeNccDelegations(resourcesToReject).allDelegationResources();
        assertThat(delegationsCache.getDelegationsCache()).hasValue(expectedValue);
        assertThat(resourceCache.allMemberResources()).isEqualTo(resourcesToReject.getCertifiableResources());
        assertThat(resourceCache.lastUpdateTime()).isGreaterThan(lastUpdate);
        assertThat(transactionTemplate.getCommits()).isOne();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldFailIfInternetResourceServicesIsNotAvailable() throws Exception {
        subject.updateFullResourceCache();
        assertThat(resourceCache.lastUpdateTime().getMillis()).isZero();
        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isOne();
    }

    @Test
    public void shouldProcessExceptions() throws Exception {
        when(resourceServicesClient.fetchAllResources()).thenThrow(new RuntimeException("RSNG BRKN"));
        subject.updateFullResourceCache();
        assertThat(resourceCache.lastUpdateTime().getMillis()).isZero();
        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldCalculateProperDiff() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));
        registryResources.put(CaName.fromMembershipId(2), IpResourceSet.parse("20.20.0.0/16, AS123"));
        registryResources.put(CaName.fromMembershipId(4), IpResourceSet.parse("30.0.0.0/8, 32.0.0.0/8"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("12.0.0.0/8, 13.0.0.0/8, 14.0.0.0/8")); //This counts as 1 resource only (12.0.0.0 - 14.255.255.255)
        localResources.put(CaName.fromMembershipId(3), IpResourceSet.parse("19.0.0.0/8, 21.0.0.0/8, AS16"));
        localResources.put(CaName.fromMembershipId(4), IpResourceSet.parse("32.0.0.0/8, 34.0.0.0/8, 36.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(7, resourceDiff.getLocalSize());
        assertEquals(6, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(1, changes1.getAdded());
        assertEquals(1, changes1.getDeleted());

        final ResourceCacheService.Changes changes2 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(2));
        assertEquals(2, changes2.getAdded());
        assertEquals(0, changes2.getDeleted());

        final ResourceCacheService.Changes changes3 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(3));
        assertEquals(0, changes3.getAdded());
        assertEquals(3, changes3.getDeleted());

        final ResourceCacheService.Changes changes4 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(4));
        assertEquals(1, changes4.getAdded());
        assertEquals(2, changes4.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExact() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(2, changes1.getAdded());
        assertEquals(0, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactTheOtherWay() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(0, changes1.getAdded());
        assertEquals(2, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactReverse() throws Exception {
        final Map<CaName, IpResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, IpResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
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

    private static class DataSamples {
        static MemberResources memberResources() {
            return new MemberResources(
                    asList(new AsnResource(1L, "AS64496", "ALLOCATED", "ORG-123")),
                    asList(new Ipv4Allocation(1L, "192.0.2.0/24", "ALLOCATED", "ORG-123")),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
        }
        static MemberResources rejectedMemberResources() {
            List<ResourceServicesClient.Ipv6Allocation> ipv6Allocations = new ArrayList<>(128);
            for (int i = 0; i <= 127; i++) {
                ipv6Allocations.add(new ResourceServicesClient.Ipv6Allocation(1L, "2001:DB8:" + Integer.toHexString(2*i)  + "::/48", "ALLOCATED", "ORG-123"));
            }
            return new MemberResources(
                    asList(new AsnResource(1L, "AS64496", "ALLOCATED", "ORG-123")),
                    asList(new Ipv4Allocation(1L, "192.0.2.0/24", "ALLOCATED", "ORG-123")),
                    emptyList(),
                    ipv6Allocations,
                    emptyList(),
                    emptyList()
            );
        }

        static TotalResources totalResources() {
            final MemberResources allMembersResources = memberResources();
            return new TotalResources(allMembersResources, ripeNccDelegations(allMembersResources));
        }

        static IpResourceSet productionCaDelegations(MemberResources memberResources) {
            return memberResources.getCertifiableResources().values().stream()
                    .collect(IpResourceSet::new, IpResourceSet::addAll, IpResourceSet::addAll);
        }

        static RipeNccDelegations ripeNccDelegations(MemberResources memberResources) {
            final List<RipeNccDelegation> ripeNccAsnDelegations = new ArrayList<>();
            final List<RipeNccDelegation> ripeNccIpv4Delegations = new ArrayList<>();
            final List<RipeNccDelegation> ripeNccIpv6Delegations = new ArrayList<>();
            memberResources.getCertifiableResources().values().forEach(rs ->
                rs.iterator().forEachRemaining(i -> {
                    switch (i.getType()) {
                        case ASN:
                            ripeNccAsnDelegations.add(new RipeNccDelegation(i.toString()));
                            return;
                        case IPv4:
                            ripeNccIpv4Delegations.add(new RipeNccDelegation(i.toString()));
                            return;
                        case IPv6:
                            ripeNccIpv6Delegations.add(new RipeNccDelegation(i.toString()));
                    }
                }));
            return new RipeNccDelegations(ripeNccAsnDelegations, ripeNccIpv4Delegations, ripeNccIpv6Delegations);
        }
    }

    private static class InMemoryDelegationsCache implements DelegationsCache {
        private final AtomicReference<IpResourceSet> delegations = new AtomicReference<>();

        @Override
        public void cacheDelegations(IpResourceSet delegations) {
            this.delegations.set(delegations);
        }

        @Override
        public Optional<IpResourceSet> getDelegationsCache() {
            return Optional.ofNullable(delegations.get());
        }
    }

    private static class TransactionOperationsSpy implements TransactionOperations {
        private final AtomicInteger commits = new AtomicInteger(0);
        private final AtomicInteger rollbacks = new AtomicInteger(0);

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            SimpleTransactionStatus status = new SimpleTransactionStatus(false);
            T ret = null;
            try {
                ret = action.doInTransaction(status);
            } catch (RuntimeException e) {
                status.setRollbackOnly();
            }
            if (status.isRollbackOnly()) {
                rollbacks.incrementAndGet();
            } else {
                commits.incrementAndGet();
            }
            return ret;
        }

        public int getCommits() {
            return commits.get();
        }

        public int getRollbacks() {
            return rollbacks.get();
        }

        public void reset() {
            commits.set(0);
            rollbacks.set(0);
        }
    }

    private static class InMemoryResourceCache implements ResourceCache {
        private final AtomicReference<Map<CaName, IpResourceSet>> cache = new AtomicReference<>(emptyMap());
        private final AtomicLong lastUpdate = new AtomicLong(0L);

        private final CaName productionCaName;

        public InMemoryResourceCache(CaName productionCaName) {
            this.productionCaName = productionCaName;
        }

        @Override
        public boolean hasNoProductionResources() {
            return cache.get().getOrDefault(productionCaName, new IpResourceSet()).isEmpty();
        }

        @Override
        public Optional<IpResourceSet> lookupResources(CaName user) {
            return Optional.ofNullable(cache.get().get(user));
        }

        @Override
        public DateTime lastUpdateTime() {
            return new DateTime(lastUpdate.get());
        }

        @Override
        public boolean hasNoMemberResources() {
            return cache.get().entrySet().stream()
                    .filter(x -> !x.getKey().equals(productionCaName))
                    .anyMatch(x -> !x.getValue().isEmpty());
        }

        @Override
        public void populateCache(Map<CaName, IpResourceSet> certifiableResources) {
            this.cache.set(certifiableResources);
            this.lastUpdate.set(System.currentTimeMillis());
        }

        @Override
        public Map<CaName, IpResourceSet> allMemberResources() {
            return cache.get().entrySet().stream()
                    .filter(x -> !x.getKey().equals(productionCaName))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}