package net.ripe.rpki.ripencc.ui.daemon;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.ResourceCacheUpToDateHealthCheck;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheUpToDateHealthCheckTest {
    @Mock(answer = Answers.RETURNS_MOCKS)
    private ResourceCacheService resourceCacheService;

    private ResourceCacheUpToDateHealthCheck subject;

    @Before
    public void setUp() {
        subject = new ResourceCacheUpToDateHealthCheck(resourceCacheService);
    }

    @Test
    public void should_be_healthy_if_cache_was_not_updated_for_less_than_configured_time() {
        // There is no elegant way to override instants created deep in our code w/o using DI or our own wrapper
        // so take a larger time distance
        var oneMillisecondBeforeThreshold = Instant.now().plusSeconds(1).minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        when(resourceCacheService.getLastUpdatedAt()).thenReturn(Optional.of(oneMillisecondBeforeThreshold));

        Health.Status check = subject.check();

        assertThat(check.isHealthy()).isTrue();
        assertThat(check.message).startsWith("last updated PT32M59S ago");
    }

    @Test
    public void should_be_unhealthy_if_cache_was_not_updated_for_8_or_more_hours() {
        var someTimeAgo = Instant.now().minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        when(resourceCacheService.getLastUpdatedAt()).thenReturn(Optional.of(someTimeAgo));

        Health.Status check = subject.check();
        assertThat(check.isHealthy()).isFalse();
        assertThat(check.isWarning()).isTrue();

        assertThat(check.message).startsWith("last updated PT33M ago");

        when(resourceCacheService.getUpdateLastAttemptedAt()).thenReturn(Optional.of(Instant.now()));
        check = subject.check();
        assertThat(check.isHealthy()).isFalse();
        assertThat(check.isWarning()).isFalse();
    }
}
