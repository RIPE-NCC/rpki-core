package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KrillNonHostedPublisherRepositoryHealthCheckTest {

    @Mock
    KrillNonHostedPublisherRepositoryBean client;
    @InjectMocks
    KrillNonHostedPublisherRepositoryHealthCheck subject;

    @Test
    public void checkHealthy() {
        when(client.isAvailable()).thenReturn(true);
        assertTrue(subject.check().isHealthy());
    }

    @Test
    public void checkUnhealthy() {
        when(client.isAvailable()).thenReturn(false);
        assertFalse(subject.check().isHealthy());
    }

}