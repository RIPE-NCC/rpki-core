package net.ripe.rpki.services.impl.background;

import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.Instant;
import org.joda.time.base.AbstractInstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ResourceCacheService {
    private static final int MAX_SIZE_CHANGE_ABSOLUTE_THRESHOLD = 200;
    private static final int MAX_PER_CA_CHANGE_ABSOLUTE_THRESHOLD = 100;
    private static final int MAX_DELEGATIONS_CHANGE_ABSOLUTE_THRESHOLD = 50;

    private static final double MAX_CHANGE_RELATIVE_THRESHOLD = 0.005;

    private final RoaConfigUpdater roaConfigUpdater;
    private final TransactionTemplate transactionTemplate;
    private final ResourceServicesClient resourceServicesClient;
    private final ResourceCache resourceCache;
    private final DelegationsCache delegationsCache;

    @Getter
    private final CaName productionCaName;
    @Getter
    private final CaName allResourcesCaName;

    @Value("${accept.one.rejected.resource.cache.update:false}")
    private volatile boolean acceptOneRejectedResourceCacheUpdate;

    private final AtomicReference<ResourceStat> resourceStats;
    private final ResourceCacheServiceMetrics resourceCacheServiceMetrics;

    @Autowired
    public ResourceCacheService(RoaConfigUpdater roaConfigUpdater,
                                TransactionTemplate transactionTemplate,
                                ResourceServicesClient resourceServicesClient,
                                ResourceCache resourceCache,
                                DelegationsCache delegationsCache,
                                @Value("${" + RepositoryConfiguration.PRODUCTION_CA_NAME + "}") X500Principal productionCaName,
                                @Value("${" + RepositoryConfiguration.ALL_RESOURCES_CA_NAME + "}") X500Principal allResourcesCaName,
                                MeterRegistry meterRegistry) {
        this.roaConfigUpdater = roaConfigUpdater;
        this.resourceServicesClient = resourceServicesClient;
        this.resourceCache = resourceCache;
        this.transactionTemplate = transactionTemplate;
        this.delegationsCache = delegationsCache;

        this.productionCaName = CaName.of(productionCaName);
        this.allResourcesCaName = CaName.of(allResourcesCaName);

        final Instant lastUpdateTimeFromDatabase = Optional
            .ofNullable(resourceCache.lastUpdateTime())
            .map(AbstractInstant::toInstant)
            .orElse(null);

        resourceStats = new AtomicReference<>(new ResourceStat(null, lastUpdateTimeFromDatabase));
        resourceCacheServiceMetrics = new ResourceCacheServiceMetrics(resourceStats, meterRegistry);
    }

    protected void updateFullResourceCache() {
        updateProductionCaCache();
        updateMembersCache();
    }

    private Optional<Rejection> maybeOneTimeOverrideRejection(Optional<Rejection> rejection) {
        if (rejection.isPresent() && acceptOneRejectedResourceCacheUpdate) {
            log.error("one-time overriding rejection for reason: {}", rejection.get().message);
            acceptOneRejectedResourceCacheUpdate = false;
            return Optional.empty();
        }
        return rejection;
    }

    protected void updateMembersCache() {
        final Map<CaName, IpResourceSet> registryResources;
        try {
            registryResources = resourceServicesClient.fetchAllMemberResources().getCertifiableResources();
        } catch (Exception e) {
            log.error("The RIPE NCC internet resources REST API is not available", e);
            resourceCacheServiceMetrics.onMemberCacheException();
            return;
        }
        
        final Map<CaName, IpResourceSet> localResources = getResourcesFromCache();
        final ResourceDiffStat resourcesDiff = resourcesDiff(registryResources, localResources);

        final Optional<Rejection> rejection = maybeOneTimeOverrideRejection(isAcceptableDiff(resourcesDiff));
        if (!rejection.isPresent()) {
            resourceStats.set(new ResourceStat(resourcesDiff, Instant.now()));
            updateResourceCache(registryResources);
            roaConfigUpdater.updateRoaConfig(registryResources);
            resourceCacheServiceMetrics.onMemberCacheAccepted();
            if (resourcesDiff.absoluteSizeDiff() == 0) {
                log.info("Resource cache has no update; remaining at {} entries", resourcesDiff.localSize);
            } else {
                log.info(
                        "Resource cache has been updated from {} entries to {}%n{}",
                        resourcesDiff.localSize,
                        resourcesDiff.registrySize,
                        showDiffSummary(resourcesDiff)
                );
            }
        } else {
            // update the resource diff, but keep the old time
            resourceStats.getAndUpdate(rs -> new ResourceStat(resourcesDiff, rs.lastUpdated));

            resourceCacheServiceMetrics.onMemberCacheRejected();
            log.error(String.format("Resource cache update has been rejected, reason: %s%n%s", rejection.get().message, rejection.get().summary));
        }
    }

    public void updateProductionCaCache() {
        IpResourceSet retrieved;
        try {
            retrieved = resourceServicesClient.findProductionCaDelegations();
        } catch (Exception e) {
            resourceCacheServiceMetrics.onDelegationsUpdateException();
            log.error("Couldn't retrieve Production CA resources, probably RSNG is not available.", e);
            return;
        }

        final IpResourceSet cached = delegationsCache.getDelegationsCache().orElse(new IpResourceSet());

        // Cached but no longer in the retrieved set, should be the ones removed.
        IpResourceSet removed = subtract(cached, retrieved);

        // Retrieved but not in the cache should be the ones added.
        IpResourceSet added = subtract(retrieved, cached);

        final DelegationDiffStat resourcesDiff = delegationsDiff(retrieved, cached);

        final Optional<Rejection> rejection = maybeOneTimeOverrideRejection(isAcceptableDiff(resourcesDiff));
        if (!rejection.isPresent()) {
            updateDelegationsCache(retrieved);
            resourceCacheServiceMetrics.onDelegationsUpdateAccepted(added, removed);
            log.info("Production CA delegations cache has been updated from {} entries to {}", resourcesDiff.localResourceCount, resourcesDiff.registrySizeResourceCount);
        } else {
            resourceCacheServiceMetrics.onDelegationsUpdateRejected();
            log.error("Production CA delegations cache update with diff {} has been rejected, reason: {}", resourcesDiff, rejection.get().message);
        }
    }

    public Optional<IpResourceSet> getProductionCaResources() {
        return delegationsCache.getDelegationsCache();
    }

    private static IpResourceSet subtract(IpResourceSet s1, IpResourceSet s2) {
        final IpResourceSet result = new IpResourceSet(s1);
        result.removeAll(s2);
        return result;
    }

    static int resourceSetSize(IpResourceSet ipr) {
        return Iterators.size(ipr.iterator());
    }

    private Map<CaName, IpResourceSet> getResourcesFromCache() {
        return resourceCache.allMemberResources();
    }

    private void updateResourceCache(Map<CaName, IpResourceSet> certifiableResources) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                resourceCache.populateCache(certifiableResources);
            }
        });
    }

    private void updateDelegationsCache(IpResourceSet retrieved) {
        transactionTemplate.execute(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        delegationsCache.cacheDelegations(retrieved);
                    }});
    }

    static ResourceDiffStat resourcesDiff(Map<CaName, IpResourceSet> registryResources, Map<CaName, IpResourceSet> localResources) {

        final Map<CaName, Changes> changesMap = new HashMap<>();
        final HashSet<CaName> casOnBoth = new HashSet<>(registryResources.keySet());
        casOnBoth.retainAll(localResources.keySet());

        casOnBoth.forEach(caName -> {
            final Changes changes = resourcesSetDiff(registryResources.get(caName), localResources.get(caName));
            changesMap.put(caName, changes);
        });

        registryResources.forEach((caName, registry) -> {
            if(!casOnBoth.contains(caName)) {
                int added = (int)StreamSupport.stream(registry.spliterator(), false).count();
                changesMap.put(caName, new Changes(added, 0));
            }
        });

        localResources.forEach((caName, local) -> {
            if(!casOnBoth.contains(caName)) {
                int deleted = (int)StreamSupport.stream(local.spliterator(), false).count();
                changesMap.put(caName, new Changes(0, deleted));
            }
        });

        int totalAdded = 0;
        int totalDeleted = 0;
        for (Map.Entry<CaName, Changes> entry : changesMap.entrySet()) {
            Changes changes = entry.getValue();
            totalAdded += changes.added;
            totalDeleted += changes.deleted;
        }

        return new ResourceDiffStat(localResources.size(), registryResources.size(), totalAdded, totalDeleted, changesMap);
    }

    static DelegationDiffStat delegationsDiff(IpResourceSet registryDelegations, IpResourceSet localDelegations) {
        final Changes changes = resourcesSetDiff(registryDelegations, localDelegations);
        return new DelegationDiffStat(
                resourceSetSize(localDelegations),
                resourceSetSize(registryDelegations),
                changes.added,
                changes.deleted);
    }

    private static Changes resourcesSetDiff(IpResourceSet newSet, IpResourceSet oldSet) {
        int added = 0;
        int deleted = 0;
        for (final IpResource r : oldSet) {
            if (!newSet.contains(r)) {
                deleted++;
            }
        }
        for (final IpResource r : newSet) {
            if (!oldSet.contains(r)) {
                added++;
            }
        }
        return new Changes(added, deleted);
    }

    private static Optional<Rejection> isAcceptableDiff(ResourceDiffStat diffStat) {
        // Bootstrap case: local cache is empty
        if (diffStat.localSize == 0) {
            return Optional.empty();
        }

        final int absoluteSizeDiff = diffStat.absoluteSizeDiff();
        if (absoluteSizeDiff > MAX_SIZE_CHANGE_ABSOLUTE_THRESHOLD) {
            return Optional.of(new Rejection(String.format("The difference in cache size (%d) is too big, " +
                "old size is %d, new size is %d", absoluteSizeDiff, diffStat.localSize, diffStat.registrySize), Optional.empty()));
        }
        final double relativeSizeDiff = diffStat.relativeSizeDiff();
        if (relativeSizeDiff > MAX_CHANGE_RELATIVE_THRESHOLD) {
            return Optional.of(new Rejection(String.format("The relative difference in cache size (%s) is too big, " +
                "old size is %d, new size is %d", relativeSizeDiff, diffStat.localSize, diffStat.registrySize), Optional.empty()));
        }

        if (diffStat.totalAdded + diffStat.totalDeleted > MAX_PER_CA_CHANGE_ABSOLUTE_THRESHOLD) {
            final String summary = showDiffSummary(diffStat);
            return Optional.of(new Rejection(
                    String.format(
                            "The sum of the per-CA changes (%d) is too big, added %d prefixes, deleted %d prefixes",
                            diffStat.totalAdded + diffStat.totalDeleted,
                            diffStat.totalAdded, diffStat.totalDeleted
                    ),
                    Optional.of(summary)
            ));
        }
        return Optional.empty();
    }

    private static String showDiffSummary(ResourceDiffStat diffStat) {
        StringBuilder builder = new StringBuilder("-------Summary-------");
        diffStat.getChangesMap().forEach((caName, changes) -> {
            if (changes.added > 0 || changes.deleted > 0) {
                builder.append("\n")
                        .append(caName).append(":\n")
                        .append("\tadded: ").append(changes.added).append("\n")
                        .append("\tdeleted: ").append(changes.deleted).append("\n")
                        .append("---------------------");
            }
        });
        return builder.toString();
    }

    private static Optional<Rejection> isAcceptableDiff(DelegationDiffStat diffStat) {
        if (diffStat.localResourceCount == 0) {
            // Bootstrap case: local cache is empty
            return Optional.empty();
        }
        if (diffStat.totalAdded + diffStat.totalDeleted > MAX_DELEGATIONS_CHANGE_ABSOLUTE_THRESHOLD) {
            return Optional.of(new Rejection(String.format("The change in the production CA delegations is too big, " +
                "added %d prefixes, deleted %d prefixes", diffStat.totalAdded, diffStat.totalDeleted), Optional.empty()));
        }
        return Optional.empty();
    }

    public Optional<IpResourceSet> getCaResources(CaName caName) {
        return resourceCache.lookupResources(caName);
    }

    @Data
    @AllArgsConstructor
    static class ResourceStat {
        private ResourceDiffStat resourceDiff;
        private Instant lastUpdated;
    }

    @Data
    @AllArgsConstructor
    static class ResourceDiffStat {
        private int localSize;
        private int registrySize;
        private int totalAdded;
        private int totalDeleted;
        private Map<CaName, Changes> changesMap;

        public int absoluteSizeDiff() {
            return Math.abs(localSize - registrySize);
        }

        public double relativeSizeDiff() {
            return 2.0 * absoluteSizeDiff() / (localSize + registrySize);
        }
    }

    @Data
    @AllArgsConstructor
    static class DelegationDiffStat {
        private int localResourceCount;
        private int registrySizeResourceCount;
        private int totalAdded;
        private int totalDeleted;
    }

    @Data
    @AllArgsConstructor
    static class Changes {
        private int added;
        private int deleted;
    }

    @Data
    @AllArgsConstructor
    static class Rejection {
        private String message;
        private Optional<String> summary;
    }

    private static class ResourceCacheServiceMetrics {
        private static final String DELEGATIONS_UPDATES_DESCRIPTION = "The number of updates to the delegations cache.";
        private static final String RESOURCE_CACHE_CHANGE = "The amount of prefixed changed for all CAs in total";
        private static final String RESOURCE_COUNT_DESCRIPTION = "The amount of resources in the given source";
        private static final String RESOURCE_UPDATES_DESCRIPTION = "The number of updates to the resource cache";

        public static final String DELEGATIONS_UPDATES_METRIC = "rpkicore.resource.delegations.updates";
        public static final String RESOURCE_UPDATES_METRIC = "rpkicore.resource.cache.update";
        public static final String RESOURCE_CACHE_CHANGE_METRIC = "rpkicore.resource.count.change";
        public static final String RESOURCE_COUNT_METRIC = "rpkicore.resource.count";

        // Metric tags
        public static final String STATUS = "status";
        public static final String SOURCE = "source";
        public static final String OPERATION = "operation";

        public final Counter resourceUpdatesAccepted;
        public final Counter resourceUpdatesRejected;
        public final Counter resourceUpdatesException;
        public final Counter delegationsUpdatesAccepted;
        public final Counter delegationsUpdatesException;
        public final Counter delegationsUpdatesRejected;

        public final AtomicDouble delegationsAdded = new AtomicDouble(0);
        public final AtomicDouble delegationsRemoved = new AtomicDouble(0);

        public ResourceCacheServiceMetrics(AtomicReference<ResourceStat> resourceStats, MeterRegistry meterRegistry) {

            Gauge.builder(RESOURCE_COUNT_METRIC, resourceStats, getMetric(ResourceDiffStat::getLocalSize))
                    .tags(SOURCE, "local")
                    .description(RESOURCE_COUNT_DESCRIPTION)
                    .register(meterRegistry);

            Gauge.builder(RESOURCE_COUNT_METRIC, resourceStats, getMetric(ResourceDiffStat::getRegistrySize))
                    .tags(SOURCE, "registry")
                    .description(RESOURCE_COUNT_DESCRIPTION)
                    .register(meterRegistry);

            Gauge.builder(RESOURCE_CACHE_CHANGE_METRIC, resourceStats, getMetric(ResourceDiffStat::getTotalAdded))
                    .tags(OPERATION, "added")
                    .description(RESOURCE_CACHE_CHANGE)
                    .register(meterRegistry);

            Gauge.builder(RESOURCE_CACHE_CHANGE_METRIC, resourceStats, getMetric(ResourceDiffStat::getTotalDeleted))
                    .tags(OPERATION, "deleted")
                    .description(RESOURCE_CACHE_CHANGE)
                    .register(meterRegistry);

            Gauge.builder("rpkicore.resource.cache.updated", resourceStats, getLastUpdated())
                    .description("Timestamp at which the last update was successful")
                    .register(meterRegistry);

            resourceUpdatesAccepted = Counter.builder(RESOURCE_UPDATES_METRIC)
                    .description(RESOURCE_UPDATES_DESCRIPTION)
                    .tag(STATUS, "accepted")
                    .register(meterRegistry);

            resourceUpdatesRejected = Counter.builder(RESOURCE_UPDATES_METRIC)
                    .description(RESOURCE_UPDATES_DESCRIPTION)
                    .tag(STATUS, "rejected")
                    .register(meterRegistry);

            resourceUpdatesException = Counter.builder(RESOURCE_UPDATES_METRIC)
                    .description(RESOURCE_UPDATES_DESCRIPTION)
                    .tag(STATUS, "exception")
                    .register(meterRegistry);

            delegationsUpdatesAccepted = Counter.builder(DELEGATIONS_UPDATES_METRIC)
                    .description(DELEGATIONS_UPDATES_DESCRIPTION)
                    .tag(STATUS, "accepted")
                    .register(meterRegistry);

            delegationsUpdatesException = Counter.builder(DELEGATIONS_UPDATES_METRIC)
                    .description(DELEGATIONS_UPDATES_DESCRIPTION)
                    .tag(STATUS, "exception")
                    .register(meterRegistry);

            delegationsUpdatesRejected = Counter.builder(DELEGATIONS_UPDATES_METRIC)
                    .description(DELEGATIONS_UPDATES_DESCRIPTION)
                    .tag(STATUS, "rejected")
                    .register(meterRegistry);

            // delegations
            Gauge.builder("rpkicore.delegations", delegationsAdded, AtomicDouble::get)
                    .description("The delegated resources added")
                    .tag(SOURCE, "resourceLookupService")
                    .tag(OPERATION, "added")
                    .register(meterRegistry);

            Gauge.builder("rpkicore.delegations", delegationsRemoved, AtomicDouble::get)
                    .description("The delegated resources deleted")
                    .tag(SOURCE, "resourceLookupService")
                    .tag(OPERATION, "removed")
                    .register(meterRegistry);
        }

        private static ToDoubleFunction<AtomicReference<ResourceStat>> getMetric(ToDoubleFunction<ResourceDiffStat> f) {
            return rs -> rs.get() == null || rs.get().resourceDiff == null ? 0 : f.applyAsDouble(rs.get().resourceDiff);
        }

        private static ToDoubleFunction<AtomicReference<ResourceStat>> getLastUpdated() {
            return rs -> rs.get() == null || rs.get().lastUpdated == null ? Double.NaN : rs.get().lastUpdated.getMillis() / 1000.0;
        }

        private static double resourceSetSizeDouble(IpResourceSet ipr) {
            return Iterators.size(ipr.iterator());
        }

        public void onDelegationsUpdateException() { delegationsUpdatesException.increment(); }

        public void onDelegationsUpdateAccepted(IpResourceSet added, IpResourceSet removed) {
            delegationsUpdatesAccepted.increment();
            delegationsAdded.set(resourceSetSizeDouble(added));
            delegationsRemoved.set(resourceSetSizeDouble(removed));
        }

        public void onDelegationsUpdateRejected() {
            delegationsUpdatesRejected.increment();
        }

        public void onMemberCacheAccepted() {
            resourceUpdatesAccepted.increment();
        }

        public void onMemberCacheRejected() {
            resourceUpdatesRejected.increment();
        }

        /**
         * Exception occurred while retrieving the content.
         */
        public void onMemberCacheException() { resourceUpdatesException.increment(); }
    }
}
