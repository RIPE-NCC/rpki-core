package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResourceCacheUpToDateHealthCheck extends Health.Check {

    public static final Duration MAX_DURATION_FOR_CACHE_UPDATE = Duration.standardMinutes(33);

    private final ResourceCacheService resourceCacheService;

    @Autowired
    public ResourceCacheUpToDateHealthCheck(ResourceCacheService resourceCacheService) {
        super("resource-cache-up-to-date");
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public Health.Status check() {
        DateTime lastUpdateTime = resourceCacheService.getLastUpdatedAt();
        if (lastUpdateTime == null) {
            return Health.error("Have not updated last-update-time at all");
        }
        if (lastUpdateTime.plus(MAX_DURATION_FOR_CACHE_UPDATE).isAfterNow()) {
            return Health.ok(humanFriendly(lastUpdateTime));
        }
        var lastUpdateAttemptedAt = resourceCacheService.getUpdateLastAttemptedAt();
        if (lastUpdateAttemptedAt.isEmpty()) {
            return Health.warning(humanFriendly(lastUpdateTime) + ", but no update was attempted yet by this node");
        }
        return Health.error(humanFriendly(lastUpdateTime));
    }

    private String humanFriendly(DateTime lastUpdateTime) {
        return String.format("last updated %s ago (at %s)",
                new Period(lastUpdateTime, new DateTime()).withMillis(0).toString(PeriodFormat.getDefault()),
                lastUpdateTime
        );
    }
}
