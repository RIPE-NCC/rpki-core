package net.ripe.rpki.ripencc.ui.daemon;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.ResourceCacheUpToDateHealthCheck;
import net.ripe.rpki.server.api.ports.ResourceCache;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class ResourceCacheUpToDateHealthCheckTest {

    @Rule public FixedDateRule rule = new FixedDateRule(new DateTime());

    @Mock ResourceCache resourceCache;

    private ResourceCacheUpToDateHealthCheck subject;

    @Before
    public void setUp() {
        subject = new ResourceCacheUpToDateHealthCheck(resourceCache);
    }

    @Test
    public void should_be_healthy_if_cache_was_not_updated_for_less_than_configured_time() {
        DateTime oneMillisecondBeforeThreshold = new DateTime().plusMillis(1).minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        given(resourceCache.lastUpdateTime()).willReturn(oneMillisecondBeforeThreshold);

        Health.Status check = subject.check();

        assertTrue(check.isHealthy());
        String expected = "last updated 32 minutes and 59 seconds ago";
        assertEquals(expected, check.message.substring(0, expected.length()));
    }

    @Test
    public void should_be_unhealthy_if_cache_was_not_updated_for_8_or_more_hours() {
        DateTime someTimeAgo = new DateTime().minus(ResourceCacheUpToDateHealthCheck.MAX_DURATION_FOR_CACHE_UPDATE);

        given(resourceCache.lastUpdateTime()).willReturn(someTimeAgo);

        Health.Status check = subject.check();
        assertFalse(check.isHealthy());

        String expected = "last updated 33 minutes ago";
        assertEquals(expected, check.message.substring(0, expected.length()));
    }
}
