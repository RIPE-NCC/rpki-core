package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.core.services.background.SequentialBackgroundQueuedTaskRunner;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.*;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.security.auth.x500.X500Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBeanTest.MEMBER_CA_1;
import static net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBeanTest.MEMBER_CA_2;
import static net.ripe.rpki.services.impl.background.ResourceCacheService.resourcesDiff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheServiceTest {
    @Rule
    public FixedDateRule rule = new FixedDateRule(new DateTime());

    private final TransactionOperationsSpy transactionTemplate = new TransactionOperationsSpy();

    private final ResourceCache resourceCache = new InMemoryResourceCache(CaName.parse("CN=RIPE NCC Resources,O=RIPE NCC,C=NL"));
    private final DelegationsCache delegationsCache = new InMemoryDelegationsCache();

    @Mock
    private ResourceServicesClient resourceServicesClient;
    @Mock
    private SequentialBackgroundQueuedTaskRunner sequentialBackgroundQueuedTaskRunner;
    @Mock
    private AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean;

    private ResourceCacheService subject;

    @Before
    public void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        subject = new ResourceCacheService(transactionTemplate, resourceServicesClient, resourceCache, delegationsCache,
            sequentialBackgroundQueuedTaskRunner, allCaCertificateUpdateServiceBean, new X500Principal("CN=666"), new X500Principal("CN=123"), new SimpleMeterRegistry());
    }

    @After
    public void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    public void shouldTrackLastUpdateAttempt() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());

        assertThat(subject.getUpdateLastAttemptedAt()).isEmpty();

        subject.updateFullResourceCache();

        assertThat(subject.getUpdateLastAttemptedAt()).hasValue(Instant.now());
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
    @SuppressWarnings("unchecked")
    public void shouldUpdateIncomingCertificatesForUpdatedCas() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());
        subject.updateFullResourceCache();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();


        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(sequentialBackgroundQueuedTaskRunner).submit(any(), runnableArgumentCaptor.capture(), any());

        runnableArgumentCaptor.getValue().run();

        ArgumentCaptor<Predicate<CertificateAuthorityData>> predicateArgumentCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(allCaCertificateUpdateServiceBean).runService(eq(Collections.emptyMap()), predicateArgumentCaptor.capture());

        Predicate<CertificateAuthorityData> caIdentityPredicate = predicateArgumentCaptor.getValue();
        assertThat(caIdentityPredicate)
                .accepts(MEMBER_CA_1)
                .rejects(MEMBER_CA_2);
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
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.totalResources());
        resourceCache.populateCache(Collections.emptyMap()); // empty cache allows for any update
        delegationsCache.cacheDelegations(DataSamples.productionCaDelegations(DataSamples.rejectedMemberResources()));

        subject.updateFullResourceCache();

        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isOne();
    }

    @Test
    public void shouldRollbackOnMemberResourceCacheRejection() {
        when(resourceServicesClient.fetchAllResources()).thenReturn(DataSamples.emptyTotalResources());
        resourceCache.populateCache(DataSamples.memberResources().getCertifiableResources());

        subject.updateFullResourceCache();

        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isOne();
    }

    @Test
    public void shouldAllowForcingOfRejectedUpdate() {
        final MemberResources memberResources = DataSamples.memberResources();
        final RipeNccDelegations ripeNccDelegations = DataSamples.ripeNccDelegations(memberResources);

        resourceCache.populateCache(memberResources.getCertifiableResources());
        delegationsCache.cacheDelegations(ripeNccDelegations.allDelegationResources());
        DateTime lastUpdate = resourceCache.lastUpdateTime();

        final MemberResources resourcesToReject = DataSamples.rejectedMemberResources();
        when(resourceServicesClient.fetchAllResources()).thenReturn(
            new TotalResources(resourcesToReject, DataSamples.ripeNccDelegations(resourcesToReject)));

        subject.updateFullResourceCache(Optional.of("477328"));

        final ImmutableResourceSet expectedValue = DataSamples.ripeNccDelegations(resourcesToReject).allDelegationResources();
        assertThat(delegationsCache.getDelegationsCache()).hasValue(expectedValue);
        assertThat(resourceCache.allMemberResources()).isEqualTo(resourcesToReject.getCertifiableResources());
        assertThat(resourceCache.lastUpdateTime()).isGreaterThan(lastUpdate);
        assertThat(transactionTemplate.getCommits()).isOne();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldFailIfInternetResourceServicesIsNotAvailable() {
        when(resourceServicesClient.fetchAllResources()).thenThrow(new RuntimeException("test"));
        subject.updateFullResourceCache();
        assertThat(resourceCache.lastUpdateTime().getMillis()).isZero();
        assertThat(transactionTemplate.getExecutes()).isZero();
    }

    @Test
    public void shouldProcessExceptions() {
        when(resourceServicesClient.fetchAllResources()).thenThrow(new RuntimeException("RSNG BRKN"));
        subject.updateFullResourceCache();
        assertThat(resourceCache.lastUpdateTime().getMillis()).isZero();
        assertThat(transactionTemplate.getCommits()).isZero();
        assertThat(transactionTemplate.getRollbacks()).isZero();
    }

    @Test
    public void shouldCalculateProperDiff() {
        final Map<CaName, ImmutableResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));
        registryResources.put(CaName.fromMembershipId(2), ImmutableResourceSet.parse("20.20.0.0/16, AS123"));
        registryResources.put(CaName.fromMembershipId(4), ImmutableResourceSet.parse("30.0.0.0/8, 32.0.0.0/8"));

        final Map<CaName, ImmutableResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("12.0.0.0/8, 13.0.0.0/8, 14.0.0.0/8")); //This counts as 1 resource only (12.0.0.0 - 14.255.255.255)
        localResources.put(CaName.fromMembershipId(3), ImmutableResourceSet.parse("19.0.0.0/8, 21.0.0.0/8, AS16"));
        localResources.put(CaName.fromMembershipId(4), ImmutableResourceSet.parse("32.0.0.0/8, 34.0.0.0/8, 36.0.0.0/8"));

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
    public void shouldCalculateProperDiffWhenPrefixesAreNotExact() {
        final Map<CaName, ImmutableResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final Map<CaName, ImmutableResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(2, changes1.getAdded());
        assertEquals(0, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactTheOtherWay() {
        final Map<CaName, ImmutableResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, ImmutableResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(0, changes1.getAdded());
        assertEquals(2, changes1.getDeleted());
    }

    @Test
    public void shouldCalculateProperDiffWhenPrefixesAreNotExactReverse() {
        final Map<CaName, ImmutableResourceSet> registryResources = new HashMap<>();
        registryResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/16, 12.0.0.0/16"));

        final Map<CaName, ImmutableResourceSet> localResources = new HashMap<>();
        localResources.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8, 12.0.0.0/8"));

        final ResourceCacheService.ResourceDiffStat resourceDiff = resourcesDiff(registryResources, localResources);
        assertEquals(2, resourceDiff.getLocalSize());
        assertEquals(2, resourceDiff.getRegistrySize());

        final ResourceCacheService.Changes changes1 = resourceDiff.getChangesMap().get(CaName.fromMembershipId(1));
        assertEquals(0, changes1.getAdded());
        assertEquals(2, changes1.getDeleted());
    }

    @Test
    public void shouldCountDelegations() {
        final ImmutableResourceSet registry = ImmutableResourceSet.parse("10.0.0.0/8, 12.0.0.0/8, 20.20.0.0/16, AS123");
        final ImmutableResourceSet local    = ImmutableResourceSet.parse("12.0.0.0/8, 21.0.0.0/8, AS16");

        ResourceCacheService.DelegationDiffStat resourceDiff = ResourceCacheService.delegationsDiff(registry, local);
        assertEquals(3, resourceDiff.getLocalResourceCount());
        assertEquals(4, resourceDiff.getRegistrySizeResourceCount());
        assertEquals(3, resourceDiff.getTotalAdded());
        assertEquals(2, resourceDiff.getTotalDeleted());
    }

    private static class DataSamples {
        static MemberResources memberResources() {
            String caName = MEMBER_CA_1.getName().getName();
            return new MemberResources(
                    List.of(new AsnResource(1L, "AS64496", "ALLOCATED", caName)),
                    List.of(new Ipv4Allocation(1L, "192.0.2.0/24", "ALLOCATED", caName)),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
        }
        static MemberResources rejectedMemberResources() {
            String caName = MEMBER_CA_1.getName().getName();
            List<ResourceServicesClient.Ipv6Allocation> ipv6Allocations = new ArrayList<>(128);
            for (int i = 0; i <= 127; i++) {
                ipv6Allocations.add(new ResourceServicesClient.Ipv6Allocation(1L, "2001:DB8:" + Integer.toHexString(2*i)  + "::/48", "ALLOCATED", caName));
            }
            return new MemberResources(
                    List.of(new AsnResource(1L, "AS64496", "ALLOCATED", caName)),
                    List.of(new Ipv4Allocation(1L, "192.0.2.0/24", "ALLOCATED", caName)),
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

        static TotalResources emptyTotalResources() {
            return new TotalResources(
                new MemberResources(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                new RipeNccDelegations(emptyList(), emptyList(), emptyList())
            );
        }

        static ImmutableResourceSet productionCaDelegations(MemberResources memberResources) {
            return memberResources.getCertifiableResources().values().stream()
                .flatMap(ImmutableResourceSet::stream)
                .collect(ImmutableResourceSet.collector());
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
        private final AtomicReference<ImmutableResourceSet> delegations = new AtomicReference<>();

        @Override
        public void cacheDelegations(ImmutableResourceSet delegations) {
            this.delegations.set(delegations);
        }

        @Override
        public Optional<ImmutableResourceSet> getDelegationsCache() {
            return Optional.ofNullable(delegations.get());
        }
    }

    @Getter
    private static class TransactionOperationsSpy implements TransactionOperations {
        private int executes = 0;
        private int commits = 0;
        private int rollbacks = 0;

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            SimpleTransactionStatus status = new SimpleTransactionStatus(false);
            try {
                executes++;
                return action.doInTransaction(status);
            } catch (RuntimeException e) {
                status.setRollbackOnly();
                throw e;
            } finally {
                if (status.isRollbackOnly()) {
                    ++rollbacks;
                } else {
                    ++commits;
                }
            }
        }
    }

    private static class InMemoryResourceCache implements ResourceCache {
        private final AtomicReference<Map<CaName, ImmutableResourceSet>> cache = new AtomicReference<>(emptyMap());
        private final AtomicLong lastUpdate = new AtomicLong(0L);

        private final CaName productionCaName;

        public InMemoryResourceCache(CaName productionCaName) {
            this.productionCaName = productionCaName;
        }

        @Override
        public boolean hasNoProductionResources() {
            return cache.get().getOrDefault(productionCaName, ImmutableResourceSet.empty()).isEmpty();
        }

        @Override
        public Optional<ImmutableResourceSet> lookupResources(CaName user) {
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
        public void populateCache(Map<CaName, ImmutableResourceSet> certifiableResources) {
            this.cache.set(certifiableResources);
            this.lastUpdate.set(System.currentTimeMillis());
        }

        @Override
        public Map<CaName, ImmutableResourceSet> allMemberResources() {
            return cache.get().entrySet().stream()
                    .filter(x -> !x.getKey().equals(productionCaName))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}