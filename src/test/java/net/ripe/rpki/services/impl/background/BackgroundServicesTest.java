package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;
import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult.Status;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackgroundServicesTest extends CertificationDomainTestCase {

    @Inject
    private BackgroundServices backgroundServices;

    @Inject
    private DefaultListableBeanFactory applicationContext;

    private BackgroundService fakeService;

    @BeforeAll
    public void setUp() {
        this.fakeService = mock(BackgroundService.class);
        when(fakeService.execute(anyMap())).thenReturn(
            new BackgroundServiceExecutionResult(0, 0, Status.SUCCESS)
        );

        applicationContext.registerSingleton("fakeService", fakeService);
    }

    @Test
    void should_trigger_service_without_parameters() {
        backgroundServices.trigger("fakeService");

        verify(fakeService, timeout(1000)).execute(Collections.emptyMap());
    }

    @Test
    void should_trigger_service_with_parameters() {
        Map<String, String> parameters = Collections.singletonMap("parameter", "value");

        backgroundServices.trigger("fakeService", parameters);

        verify(fakeService, timeout(1000)).execute(eq(parameters));
    }

    @Test
    void should_reject_triggering_unknown_service() {
        assertThatThrownBy(() -> backgroundServices.trigger("badService"))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
