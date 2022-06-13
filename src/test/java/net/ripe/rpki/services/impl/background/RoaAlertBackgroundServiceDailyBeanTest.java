package net.ripe.rpki.services.impl.background;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceClassData;
import net.ripe.rpki.server.api.dto.ResourceClassDataSet;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RoaAlertBackgroundServiceDailyBeanTest {

    private static final CertificateAuthorityData CA_DATA = new HostedCertificateAuthorityData(
        new VersionedId(23L, 2L), new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
        IpResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    public static final RoaAlertConfigurationData ALERT_SUBSCRIPTION_DATA = new RoaAlertConfigurationData(CA_DATA,
            new RoaAlertSubscriptionData("joeok@example.com", Arrays.asList(RouteValidityState.INVALID_ASN,
                    RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN), RoaAlertFrequency.DAILY));
    private static final RoaAlertConfigurationData ALERT_SUBSCRIPTION_ERROR = new RoaAlertConfigurationData(CA_DATA,
            new RoaAlertSubscriptionData("errorjohn@example.com", Arrays.asList(RouteValidityState.INVALID_ASN,
                    RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN), RoaAlertFrequency.DAILY));

    @Mock
    private ActiveNodeService propertyEntityService;
    @Mock
    private RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    @Mock
    private RoaAlertChecker roaAlertChecker;

    private RoaAlertBackgroundServiceDailyBean subject;

    @Before
    public void setup() {
        subject = new RoaAlertBackgroundServiceDailyBean(propertyEntityService, roaAlertConfigurationViewService, roaAlertChecker);
    }

    @Test
    public void shouldCheckEveryDailySubscription() {
        when(roaAlertConfigurationViewService.findByFrequency(RoaAlertFrequency.DAILY)).thenReturn(Collections.singletonList(ALERT_SUBSCRIPTION_DATA));

        subject.runService();

        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA);
        verifyNoMoreInteractions(roaAlertChecker);
    }

    @Test
    public void shouldHandleExceptionPerSubscription() {
        when(roaAlertConfigurationViewService.findByFrequency(RoaAlertFrequency.DAILY)).thenReturn(Arrays.asList(ALERT_SUBSCRIPTION_DATA, ALERT_SUBSCRIPTION_ERROR));
        doThrow(new RuntimeException("testing")).when(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_ERROR);

        subject.runService();

        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_ERROR);
        verify(roaAlertChecker).checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA);
        verifyNoMoreInteractions(roaAlertChecker);
    }
}
