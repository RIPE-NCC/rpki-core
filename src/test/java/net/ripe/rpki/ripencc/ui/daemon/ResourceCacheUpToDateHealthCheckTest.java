package net.ripe.rpki.ripencc.ui.daemon;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.ResourceCacheUpToDateHealthCheck;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheUpToDateHealthCheckTest {

    @Rule public FixedDateRule rule = new FixedDateRule(new DateTime());

    @Mock(answer = Answers.RETURNS_MOCKS)
    private ResourceCacheService resourceCacheService;

    private ResourceCacheUpToDateHealthCheck subject;

    @Before
    public void setUp() {
        subject = new ResourceCacheUpToDateHealthCheck(resourceCacheService);
    }

    @Test
    public void should_be_healthy_if_cache_was_not_updated_for_less_than_configured_time() {
        DateTime oneMillisecondBeforeThreshold = new DateTime().plusMillis(1).minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        when(resourceCacheService.getLastUpdatedAt()).thenReturn(oneMillisecondBeforeThreshold);

        Health.Status check = subject.check();

        assertThat(check.isHealthy()).isTrue();
        assertThat(check.message).startsWith("last updated 32 minutes and 59 seconds ago");
    }

    @Test
    public void should_be_unhealthy_if_cache_was_not_updated_for_8_or_more_hours() {
        DateTime someTimeAgo = new DateTime().minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        when(resourceCacheService.getLastUpdatedAt()).thenReturn(someTimeAgo);

        Health.Status check = subject.check();
        assertThat(check.isHealthy()).isFalse();
        assertThat(check.isWarning()).isTrue();

        assertThat(check.message).startsWith("last updated 33 minutes ago");

        when(resourceCacheService.getUpdateLastAttemptedAt()).thenReturn(Optional.of(Instant.now()));
        check = subject.check();
        assertThat(check.isHealthy()).isFalse();
        assertThat(check.isWarning()).isFalse();
    }
}
