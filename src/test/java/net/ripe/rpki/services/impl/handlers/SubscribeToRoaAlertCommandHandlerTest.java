package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.services.impl.EmailSender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static net.ripe.rpki.domain.ProductionCertificateAuthorityTest.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.Silent.class)
public class SubscribeToRoaAlertCommandHandlerTest {

    private static final Long TEST_CA_ID = 2L;

    private static final VersionedId TEST_VERSIONED_CA_ID = new VersionedId(TEST_CA_ID);

    private HostedCertificateAuthority certificateAuthority;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private RoaAlertConfigurationRepository repository;

    @Mock
    private EmailSender emailSender;

    private ArgumentCaptor<String> emailCapture;

    private ArgumentCaptor<RoaAlertConfiguration> alertCapture;

    private SubscribeToRoaAlertCommandHandler subject;

    @Before
    public void setUp() {
        certificateAuthority = createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        emailCapture = ArgumentCaptor.forClass(String.class);
        alertCapture = ArgumentCaptor.forClass(RoaAlertConfiguration.class);

        subject = new SubscribeToRoaAlertCommandHandler(certificateAuthorityRepository, repository, emailSender);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertSame(SubscribeToRoaAlertCommand.class, subject.commandType());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldCreateRoaAlertSubscriptionAndSendConfirmationEmail() {
        final String email = "joe@example.com";
        when(certificateAuthorityRepository.findHostedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(repository.findByCertificateAuthorityIdOrNull(TEST_CA_ID)).thenReturn(null);

        subject.handle(new SubscribeToRoaAlertCommand(TEST_VERSIONED_CA_ID, email,
                EnumSet.of(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH,
                        RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY));

        verify(repository).add(alertCapture.capture());
        verify(emailSender).sendEmail(emailCapture.capture(), isA(String.class),
                eq("email-templates/subscribe-confirmation-weekly.vm"), isA(Map.class));
        assertEquals(RoaAlertFrequency.WEEKLY, alertCapture.getValue().getFrequency());
        assertEquals(email, emailCapture.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRoaAlertSubscriptionAndNotSendConfirmationEmail() {
        final String email = "joe@example.com";
        final Collection<RouteValidityState> oldValidityStates = EnumSet.of(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH);
        final Collection<RouteValidityState> newValidityStates = EnumSet.of(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN);
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority, email,
                oldValidityStates, RoaAlertFrequency.DAILY);

//        when(certificateAuthorityRepository.findHostedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(repository.findByCertificateAuthorityIdOrNull(TEST_CA_ID)).thenReturn(configuration);

        subject.handle(new SubscribeToRoaAlertCommand(TEST_VERSIONED_CA_ID, email, newValidityStates));

        assertEquals(newValidityStates, configuration.getSubscriptionOrNull().getRouteValidityStates());
        verify(emailSender, times(0)).sendEmail(anyString(), isA(String.class),
                eq("email-templates/subscribe-confirmation-daily.vm"), isA(Map.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRoaAlertSubscriptionAndSendConfirmationEmails() {
        final String oldEmail = "joe@example.com";
        final String newEmail = "johnny@example.com";
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority, oldEmail,
                EnumSet.of(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH), RoaAlertFrequency.DAILY);

        when(repository.findByCertificateAuthorityIdOrNull(TEST_CA_ID)).thenReturn(configuration);

        subject.handle(new SubscribeToRoaAlertCommand(TEST_VERSIONED_CA_ID, newEmail, EnumSet.of(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH)));

        verify(emailSender, times(1)).sendEmail(eq(newEmail), isA(String.class),
                eq("email-templates/subscribe-confirmation-daily.vm"), isA(Map.class));
        List<String> emails = configuration.getSubscriptionOrNull().getEmails();
        assertTrue(emails.contains(oldEmail));
        assertTrue(emails.contains(newEmail));
    }
}
