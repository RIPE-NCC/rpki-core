package net.ripe.rpki.services.impl.background;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.rpki.core.services.background.SequentialBackgroundQueuedTaskRunner;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.util.JdbcDBComponent;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import javax.security.auth.x500.X500Principal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ResourceCacheService {
    private static final int MAX_SIZE_CHANGE_ABSOLUTE_THRESHOLD = 200;
    private static final int MAX_PER_CA_CHANGE_ABSOLUTE_THRESHOLD = 100;
    private static final int MAX_DELEGATIONS_CHANGE_ABSOLUTE_THRESHOLD = 50;

    private static final double MAX_CHANGE_RELATIVE_THRESHOLD = 0.005;

    private final TransactionOperations transactionTemplate;
    private final ResourceServicesClient resourceServicesClient;
    private final ResourceCache resourceCache;
    private final DelegationsCache delegationsCache;

    private final SequentialBackgroundQueuedTaskRunner sequentialBackgroundQueuedTaskRunner;
    private final AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean;

    @Getter
    private final CaName productionCaName;
    @Getter
    private final CaName allResourcesCaName;

    private final AtomicReference<ResourceStat> resourceStats;
    private final ResourceCacheServiceMetrics resourceCacheServiceMetrics;

    @Autowired
    public ResourceCacheService(
        TransactionOperations transactionTemplate,
        ResourceServicesClient resourceServicesClient,
        ResourceCache resourceCache,
        DelegationsCache delegationsCache,
        SequentialBackgroundQueuedTaskRunner sequentialBackgroundQueuedTaskRunner,
        AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean,
        @Value("${" + RepositoryConfiguration.PRODUCTION_CA_NAME + "}") X500Principal productionCaName,
        @Value("${" + RepositoryConfiguration.ALL_RESOURCES_CA_NAME + "}") X500Principal allResourcesCaName,
        MeterRegistry meterRegistry
    ) {
        this.resourceServicesClient = resourceServicesClient;
        this.resourceCache = resourceCache;
        this.transactionTemplate = transactionTemplate;
        this.delegationsCache = delegationsCache;
        this.sequentialBackgroundQueuedTaskRunner = sequentialBackgroundQueuedTaskRunner;
        this.allCaCertificateUpdateServiceBean = allCaCertificateUpdateServiceBean;

        this.productionCaName = CaName.of(productionCaName);
        this.allResourcesCaName = CaName.of(allResourcesCaName);

        resourceStats = new AtomicReference<>(new ResourceStat(
            resourceCache.lastUpdateTime(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        ));
        resourceCacheServiceMetrics = new ResourceCacheServiceMetrics(resourceStats, meterRegistry);
    }

    public Optional<ImmutableResourceSet> getProductionCaResources() {
        return delegationsCache.getDelegationsCache();
    }

    public ResourceStat getResourceStats() {
        return resourceStats.get();
    }

    public Optional<Instant> getLastUpdatedAt() {
        return resourceCache.lastUpdateTime();
    }

    public void updateFullResourceCache() {
        updateFullResourceCache(Optional.empty());
    }

    /**
     *
     * @param forceUpdateCode code to force a big change to be accepted
     * @return whether update was applied.
     */
    public boolean updateFullResourceCache(Optional<String> forceUpdateCode) {
        ResourceServicesClient.TotalResources allResources;
        try {
            allResources = resourceServicesClient.fetchAllResources();
        } catch (Exception e) {
            log.error("The RIPE NCC internet resources REST API is not available", e);
            resourceCacheServiceMetrics.onMemberCacheException();
            resourceCacheServiceMetrics.onDelegationsUpdateException();
            return false;
        } finally {
            // Update this field after the attempt was completed (successfully or not) so that a WARN situation
            // does not temporarily turn into an ERROR situation (see ResourceCacheUpToDateHealthCheck).
            this.resourceStats.getAndUpdate(x -> x.withUpdateLastAttemptedAt(Optional.of(Instant.now())));
        }

        return transactionTemplate.execute(status -> {
            final Update productionUpdate = productionResourcesUpdate(allResources.allDelegationResources());
            final Update membersUpdate = memberResourcesUpdate(allResources.getAllMembersResources());

            final List<Update> updates = List.of(productionUpdate, membersUpdate);
            final List<Update> rejected = updates.stream().filter(Update::isRejected).toList();

            String expectedForceUpdateVerificationCode = resourceStats.get().expectedForceUpdateVerificationCode();
            boolean forceUpdate = forceUpdateCode.stream().anyMatch(code -> Objects.equals(code, expectedForceUpdateVerificationCode));
            if (forceUpdate && !rejected.isEmpty()) {
                log.error(
                    "Forced overriding rejection for reason(s): {}",
                    rejected.stream()
                        .flatMap(x -> x.getRejectionMessage().stream())
                        .collect(Collectors.joining("; ", "", "."))
                );
            }

            interpretUpdate(productionUpdate, forceUpdate);
            interpretUpdate(membersUpdate, forceUpdate);

            JdbcDBComponent.afterCommit(() -> {
                resourceStats.getAndUpdate(rs -> rs
                    .withLastUpdatedAt(Optional.of(Instant.now()))
                    .withResourceUpdateRejection(Optional.empty())
                    .withDelegationUpdateRejection(Optional.empty())
                );
                scheduleResourceCertificateUpdateForChangedCas(updates);
            });

            if (!forceUpdate && !rejected.isEmpty()) {
                // Rollback transactions after interpreting updates so that metrics get adjusted correctly,
                // otherwise we could just return early before interpreting the updates.
                status.setRollbackOnly();
                log.error("Resource cache update rolled back since force update was not specified or verification code did not match {}", expectedForceUpdateVerificationCode);
                return false;
            }
            return true;
        });
    }

    private void scheduleResourceCertificateUpdateForChangedCas(List<Update> updates) {
        Set<X500Principal> changedCas = updates.stream()
            .flatMap(update -> update.changes.entrySet().stream())
            .filter(entry -> entry.getValue().added > 0 || entry.getValue().deleted > 0)
            .map(entry -> entry.getKey().getPrincipal())
            .collect(Collectors.toSet());
        if (changedCas.isEmpty()) {
            return;
        }

        Runnable action = () -> allCaCertificateUpdateServiceBean.runService(
            Collections.emptyMap(),
            ca -> {
                switch (ca.getType()) {
                    case HOSTED: case NONHOSTED:
                        return changedCas.contains(ca.getName());
                    case ALL_RESOURCES: case ROOT: case INTERMEDIATE:
                        return true;
                }
                throw new IllegalStateException(String.format("unknown type '%s' for CA '%s'", ca.getType(), ca.getName()));
            }
        );
        sequentialBackgroundQueuedTaskRunner.submit(
            "update CA certificates after resource cache update",
            action,
            exception -> {
            }
        );
    }

    private void interpretUpdate(Update r, boolean acceptUpdateAnyway) {
        if (acceptUpdateAnyway || !r.isRejected()) {
            r.getAccepted().run();
        } else {
            r.getRejected().accept(r.getRejection().get());
        }
    }

    private Update productionResourcesUpdate(final ImmutableResourceSet retrieved) {
        final ImmutableResourceSet cached = delegationsCache.getDelegationsCache().orElse(ImmutableResourceSet.empty());

        // Cached but no longer in the retrieved set, should be the ones removed.
        ImmutableResourceSet removed = cached.difference(retrieved);

        // Retrieved but not in the cache should be the ones added.
        ImmutableResourceSet added = retrieved.difference(cached);

        final DelegationDiffStat resourcesDiff = delegationsDiff(retrieved, cached);

        final Runnable accepted = () -> {
            delegationsCache.cacheDelegations(retrieved);
            resourceCacheServiceMetrics.onDelegationsUpdateAccepted(added, removed);

            if (resourcesDiff.totalMutations() == 0) {
                log.info("Production CA delegations cache has no update, remaining at {} entries", resourcesDiff.localResourceCount);
                return;
            }

            log.info(
                "Production CA delegations cache has been updated from {} entries to {} (+{}/-{}).",
                resourcesDiff.localResourceCount,
                resourcesDiff.registrySizeResourceCount,
                resourcesDiff.totalAdded,
                resourcesDiff.totalDeleted
            );
        };

        final Consumer<Rejection> rejected = x -> {
            resourceCacheServiceMetrics.onDelegationsUpdateRejected();
            log.error("Production CA delegations cache update with diff {} has been rejected, reason: {}", resourcesDiff, x.message);
        };

        Optional<Rejection> delegationUpdateRejection = isAcceptableDiff(resourcesDiff);
        resourceStats.getAndUpdate(rs -> rs
            .withDelegationUpdateRejection(delegationUpdateRejection)
            .withDelegationDiff(Optional.of(resourcesDiff))
        );

        return new Update(
            Collections.singletonMap(productionCaName, new Changes(
                Iterators.size(added.iterator()),
                Iterators.size(removed.iterator())
            )),
            delegationUpdateRejection,
            accepted,
            rejected
        );
    }


    private Update memberResourcesUpdate(ResourceServicesClient.MemberResources memberResources) {
        final Map<String, Integer> certifiableResourcesCounts = memberResources.getMemberResourcesCounts();
        final Map<CaName, ImmutableResourceSet> registryResources = memberResources.getCertifiableResources();
        /// Make sure this is in one long line to prevent multiple messages in the logfile, which may be interleaved.
        if (log.isInfoEnabled()) {
            final StringBuilder out = new StringBuilder("Fetched resources from RSNG:\n");
            certifiableResourcesCounts.forEach((resource, count) -> out.append(String.format("   %-20s: %d%n", resource, count)));
            out.append(String.format("Fetched resources total: %d%n", certifiableResourcesCounts.values().stream().reduce(0, Integer::sum)))
                .append(String.format("Certifiable resources  : %d", accumulateResourcesSize(registryResources)));
            log.info(out.toString());
        }

        final Map<CaName, ImmutableResourceSet> localResources = resourceCache.allMemberResources();
        final ResourceDiffStat resourcesDiff = resourcesDiff(registryResources, localResources);

        Runnable accepted = () -> {
            resourceCache.populateCache(registryResources);
            resourceCacheServiceMetrics.onMemberCacheAccepted();
            if (resourcesDiff.totalPerCaMutations() == 0) {
                log.info("Resource cache has no update; remaining at {} entries", resourcesDiff.localSize);
            } else {
                log.info(
                    "Resource cache has been updated from {} entries to {} (+{}/-{})\n{}",
                    resourcesDiff.localSize,
                    resourcesDiff.registrySize,
                    resourcesDiff.totalAdded,
                    resourcesDiff.totalDeleted,
                    showDiffSummary(resourcesDiff)
                );
            }
        };

        final Consumer<Rejection> rejected = x -> {
            resourceCacheServiceMetrics.onMemberCacheRejected();
            log.error("Resource cache update has been rejected, reason: {}\n{}}", x.message, x.summary);
        };

        Optional<Rejection> resourceUpdateRejection = isAcceptableDiff(resourcesDiff);
        resourceStats.getAndUpdate(rs -> rs
            .withResourceUpdateRejection(resourceUpdateRejection)
            .withResourceDiff(Optional.of(resourcesDiff))
        );

        return new Update(resourcesDiff.getChangesMap(), resourceUpdateRejection, accepted, rejected);
    }

    static int resourceSetSize(ImmutableResourceSet ipr) {
        return Iterators.size(ipr.iterator());
    }

    static ResourceDiffStat resourcesDiff(Map<CaName, ImmutableResourceSet> registryResources, Map<CaName, ImmutableResourceSet> localResources) {

        final Map<CaName, Changes> changesMap = new HashMap<>();
        final HashSet<CaName> casOnBoth = new HashSet<>(registryResources.keySet());
        casOnBoth.retainAll(localResources.keySet());

        casOnBoth.forEach(caName -> {
            final Changes changes = resourcesSetDiff(registryResources.get(caName), localResources.get(caName));
            changesMap.put(caName, changes);
        });

        registryResources.forEach((caName, registry) -> {
            if(!casOnBoth.contains(caName)) {
                int added = resourceSetSize(registry);
                changesMap.put(caName, new Changes(added, 0));
            }
        });

        localResources.forEach((caName, local) -> {
            if(!casOnBoth.contains(caName)) {
                int deleted = resourceSetSize(local);
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
        Integer localSize = accumulateResourcesSize(localResources);
        Integer registrySize = accumulateResourcesSize(registryResources);

        return new ResourceDiffStat(localSize, registrySize, totalAdded, totalDeleted, changesMap);
    }

    private static Integer accumulateResourcesSize(Map<CaName, ImmutableResourceSet> resourcesMap) {
        return resourcesMap.values().stream().map(ResourceCacheService::resourceSetSize).reduce(0, Integer::sum);
    }

    static DelegationDiffStat delegationsDiff(ImmutableResourceSet registryDelegations, ImmutableResourceSet localDelegations) {
        final Changes changes = resourcesSetDiff(registryDelegations, localDelegations);
        return new DelegationDiffStat(
                resourceSetSize(localDelegations),
                resourceSetSize(registryDelegations),
                changes.added,
                changes.deleted);
    }

    private static Changes resourcesSetDiff(ImmutableResourceSet newSet, ImmutableResourceSet oldSet) {
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

        if (diffStat.totalPerCaMutations() > MAX_PER_CA_CHANGE_ABSOLUTE_THRESHOLD) {
            final String summary = showDiffSummary(diffStat);
            return Optional.of(new Rejection(
                    String.format(
                            "The sum of all per-CA changes (%d) is too big, added %d prefixes, deleted %d prefixes",
                            diffStat.totalPerCaMutations(),
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
        if (diffStat.totalMutations() > MAX_DELEGATIONS_CHANGE_ABSOLUTE_THRESHOLD) {
            return Optional.of(new Rejection(String.format("The change in the production CA delegations is too big, " +
                "added %d prefixes, deleted %d prefixes", diffStat.totalAdded, diffStat.totalDeleted), Optional.empty()));
        }
        return Optional.empty();
    }

    public Optional<ImmutableResourceSet> getCaResources(CaName caName) {
        return resourceCache.lookupResources(caName);
    }

    public Optional<Instant> getUpdateLastAttemptedAt() {
        return resourceStats.get().getUpdateLastAttemptedAt();
    }

    @lombok.Value
    @lombok.With
    public static class ResourceStat {
        Optional<Instant> lastUpdatedAt;
        Optional<Instant> updateLastAttemptedAt;

        Optional<DelegationDiffStat> delegationDiff;
        Optional<ResourceDiffStat> resourceDiff;

        Optional<Rejection> delegationUpdateRejection;
        Optional<Rejection> resourceUpdateRejection;

        @JsonProperty("updateVerificationCode")
        public String expectedForceUpdateVerificationCode() {
            String s = delegationUpdateRejection.map(Rejection::getMessage).orElse("") + ":" + resourceUpdateRejection.map(Rejection::getMessage).orElse("");
            var hash = Hashing.sha256().hashString(s, StandardCharsets.UTF_8);
            return String.format("%06d", Math.floorMod(hash.asInt(), 1_000_000));
        }
    }

    @Data
    @AllArgsConstructor
    public static class ResourceDiffStat {
        private int localSize;
        private int registrySize;
        private int totalAdded;
        private int totalDeleted;
        private Map<CaName, Changes> changesMap;

        public int totalPerCaMutations() {
            return totalAdded + totalDeleted;
        }

        public int absoluteSizeDiff() {
            return Math.abs(localSize - registrySize);
        }

        public double relativeSizeDiff() {
            return 2.0 * absoluteSizeDiff() / (localSize + registrySize);
        }
    }

    @lombok.Value
    public static class DelegationDiffStat {
        int localResourceCount;
        int registrySizeResourceCount;
        int totalAdded;
        int totalDeleted;

        public DelegationDiffStat(int localResourceCount, int registrySizeResourceCount, int totalAdded, int totalDeleted) {
            Preconditions.checkArgument(localResourceCount >= 0);
            Preconditions.checkArgument(registrySizeResourceCount >= 0);
            Preconditions.checkArgument(totalAdded >= 0);
            Preconditions.checkArgument(totalDeleted >= 0);

            this.localResourceCount = localResourceCount;
            this.registrySizeResourceCount = registrySizeResourceCount;
            this.totalAdded = totalAdded;
            this.totalDeleted = totalDeleted;
        }

        public int totalMutations() {
            return totalAdded + totalDeleted;
        }
    }

    @lombok.Value
    public static class Changes {
        int added;
        int deleted;

        public Changes(final int added, final int deleted) {
            Preconditions.checkArgument(added >= 0);
            Preconditions.checkArgument(deleted >= 0);
            this.added = added;
            this.deleted = deleted;
        }
    }

    @lombok.Value
    public static class Rejection {
        String message;
        Optional<String> summary;
    }

    @lombok.Value
    static class Update {
        Map<CaName, Changes> changes;
        Optional<Rejection> rejection;
        Runnable accepted;
        Consumer<Rejection> rejected;

        public boolean isRejected() {
            return rejection.isPresent();
        }

        public Optional<String> getRejectionMessage() {
            return rejection.map(Rejection::getMessage);
        }
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
            return rs -> Optional.ofNullable(rs.get()).flatMap(x -> x.resourceDiff).map(f::applyAsDouble).orElse(0.0);
        }

        private static ToDoubleFunction<AtomicReference<ResourceStat>> getLastUpdated() {
            return rs -> Optional.ofNullable(rs.get()).flatMap(x -> x.lastUpdatedAt).map(x -> x.toEpochMilli()/1000.0).orElse(Double.NaN);
        }

        private static double resourceSetSizeDouble(ImmutableResourceSet ipr) {
            return Iterators.size(ipr.iterator());
        }

        public void onDelegationsUpdateException() { delegationsUpdatesException.increment(); }

        public void onDelegationsUpdateAccepted(ImmutableResourceSet added, ImmutableResourceSet removed) {
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

    interface CacheUpdate {
        void run(TransactionStatus status);

        @lombok.AllArgsConstructor
        class Apply implements CacheUpdate {
            Runnable effect;

            @Override
            public void run(TransactionStatus status) {
                if (!status.isRollbackOnly()) {
                    effect.run();
                }
            }
        }

        @lombok.AllArgsConstructor
        class Reject implements CacheUpdate {
            Runnable trackRejection;

            @Override
            public void run(TransactionStatus status) {
                status.setRollbackOnly();
                trackRejection.run();
            }
        }
    }
}
