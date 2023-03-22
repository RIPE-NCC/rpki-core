package net.ripe.rpki.web;

import lombok.Value;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.util.*;

import static net.ripe.rpki.services.impl.background.BackgroundServices.RESOURCE_CACHE_UPDATE_SERVICE;
import static net.ripe.rpki.util.Streams.toSortedMap;

@Controller
@RequestMapping(ResourceCacheController.RESOURCE_CACHE_PATH)
public class ResourceCacheController extends BaseController {
    public static final String RESOURCE_CACHE_PATH = "/admin/resource-cache";
    private final ResourceCacheService resourceCacheService;
    private final BackgroundService resourceCacheUpdateService;

    @Inject
    public ResourceCacheController(
        RepositoryConfiguration repositoryConfiguration,
        ActiveNodeService activeNodeService,
        ResourceCacheService resourceCacheService,
        @Named(RESOURCE_CACHE_UPDATE_SERVICE) BackgroundService resourceCacheUpdateService
    ) {
        super(repositoryConfiguration, activeNodeService);
        this.resourceCacheService = resourceCacheService;
        this.resourceCacheUpdateService = resourceCacheUpdateService;
    }

    @ModelAttribute(name = "resourceStats", binding = false)
    public ResourceCacheUpdateStats resourceStats() {
        return new ResourceCacheUpdateStats(resourceCacheService.getResourceStats());
    }

    @ModelAttribute(name = "service", binding = false)
    public BackgroundServiceData resourceCacheUpdateService() {
        return new BackgroundServiceData(RESOURCE_CACHE_UPDATE_SERVICE, resourceCacheUpdateService);
    }

    @GetMapping
    public ModelAndView index() {
        return new ModelAndView("admin/resource-cache");
    }

    @Value
    static class ResourceCacheUpdateStats {
        Optional<Instant> lastUpdatedAt;
        Optional<Instant> updateLastAttemptedAt;
        boolean rejected;
        Optional<String> delegationUpdateRejectionReason;
        Optional<String> resourceUpdateRejectionReason;
        Optional<ResourceCacheService.DelegationDiffStat> changedDelegations;
        SortedMap<CaName, ResourceCacheService.Changes> changedResources;
        String expectedForceUpdateVerificationCode;

        public ResourceCacheUpdateStats(ResourceCacheService.ResourceStat resourceStat) {
            this.rejected = resourceStat.getDelegationUpdateRejection().isPresent() || resourceStat.getResourceUpdateRejection().isPresent();
            this.delegationUpdateRejectionReason = resourceStat.getDelegationUpdateRejection().map(x -> x.getMessage());
            this.resourceUpdateRejectionReason = resourceStat.getResourceUpdateRejection().map(x -> x.getMessage());
            this.changedDelegations = resourceStat.getDelegationDiff().filter(x -> x.totalMutations() > 0);

            var caNameComparator = Comparator.comparing(CaName::toString);
            this.changedResources = resourceStat.getResourceDiff()
                .map(diff -> diff.getChangesMap().entrySet().stream()
                    .filter(entry -> entry.getValue().getAdded() > 0 || entry.getValue().getDeleted() > 0)
                    .collect(toSortedMap(Map.Entry::getKey, Map.Entry::getValue, caNameComparator)))
                .orElse(new TreeMap<>(caNameComparator));

            this.lastUpdatedAt = resourceStat.getLastUpdatedAt().map(t -> Instant.ofEpochMilli(t.getMillis()));
            this.updateLastAttemptedAt = resourceStat.getUpdateLastAttemptedAt().map(t -> Instant.ofEpochMilli(t.getMillis()));
            this.expectedForceUpdateVerificationCode = resourceStat.expectedForceUpdateVerificationCode();
        }
    }
}
