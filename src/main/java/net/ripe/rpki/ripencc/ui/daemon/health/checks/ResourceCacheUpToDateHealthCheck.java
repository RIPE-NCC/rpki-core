package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class ResourceCacheUpToDateHealthCheck extends Health.Check {

    public static final Duration MAX_DURATION_FOR_CACHE_UPDATE = Duration.ofMinutes(33);

    private final ResourceCacheService resourceCacheService;

    @Autowired
    public ResourceCacheUpToDateHealthCheck(ResourceCacheService resourceCacheService) {
        super("resource-cache-up-to-date");
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public Health.Status check() {
        var maybeLastUpdateTime = resourceCacheService.getLastUpdatedAt();
        if (maybeLastUpdateTime.isEmpty()) {
            return Health.error("Have not updated last-update-time at all");
        }

        var lastUpdatedAt = maybeLastUpdateTime.get();
        if (lastUpdatedAt.plus(MAX_DURATION_FOR_CACHE_UPDATE).isAfter(Instant.now())) {
            return Health.ok(humanFriendly(lastUpdatedAt));
        }
        return resourceCacheService.getUpdateLastAttemptedAt().map(
                lastAttempt -> Health.error(humanFriendly(lastAttempt))
        ).orElseGet(() -> Health.warning(humanFriendly(lastUpdatedAt) + ", but no update was attempted yet by this node"));
    }

    private String humanFriendly(Instant lastUpdateTime) {
        return String.format("last updated %s ago (at %s)",
                Duration.between(lastUpdateTime, Instant.now()).truncatedTo(ChronoUnit.SECONDS),
                Date.from(lastUpdateTime)
        );
    }
}
