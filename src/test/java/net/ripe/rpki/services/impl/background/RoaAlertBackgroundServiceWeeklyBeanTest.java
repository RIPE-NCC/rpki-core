package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.RoaAlertChecker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RoaAlertBackgroundServiceWeeklyBeanTest {

    private static final CertificateAuthorityData CA_DATA = new ManagedCertificateAuthorityData(new VersionedId(23L, 2L),
        new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
        ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    private static final RoaAlertConfigurationData ALERT_SUBSCRIPTION_ERROR = new RoaAlertConfigurationData(CA_DATA,
            new RoaAlertSubscriptionData("errorjohn@example.com", Arrays.asList(RouteValidityState.INVALID_ASN,
                    RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY, false));

    private static final RoaAlertConfigurationData ALERT_SUBSCRIPTION_WEEKLY = new RoaAlertConfigurationData(CA_DATA,
            new RoaAlertSubscriptionData("weeklyjoe@example.com", Arrays.asList(RouteValidityState.INVALID_ASN,
                    RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY, true));

    @Mock
    private ActiveNodeService activeNodeService;
    @Mock
    private RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    @Mock
    private RoaAlertChecker roaAlertChecker;

    private RoaAlertBackgroundServiceWeeklyBean subject;

    @Before
    public void setup() {
        subject = new RoaAlertBackgroundServiceWeeklyBean(new BackgroundTaskRunner(
                activeNodeService, new SimpleMeterRegistry()), roaAlertConfigurationViewService, roaAlertChecker);
    }

    @Test
    public void shouldCheckEveryWeekSubscription() {
        when(roaAlertConfigurationViewService.findByFrequency(RoaAlertFrequency.WEEKLY)).thenReturn(Collections.singletonList(ALERT_SUBSCRIPTION_WEEKLY));

        subject.runService(Collections.emptyMap());

        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_WEEKLY);
        verifyNoMoreInteractions(roaAlertChecker);
    }

    @Test
    public void shouldHandleExceptionPerSubscription() {
        when(roaAlertConfigurationViewService.findByFrequency(RoaAlertFrequency.WEEKLY)).thenReturn(Arrays.asList(ALERT_SUBSCRIPTION_ERROR, ALERT_SUBSCRIPTION_WEEKLY));
        doThrow(new RuntimeException("testing")).when(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_ERROR);

        subject.runService(Collections.emptyMap());

        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_ERROR);
        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_WEEKLY);
        verifyNoMoreInteractions(roaAlertChecker);
    }
}
