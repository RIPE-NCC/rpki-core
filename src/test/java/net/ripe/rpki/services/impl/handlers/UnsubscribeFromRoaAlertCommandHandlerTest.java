package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.commands.UnsubscribeFromRoaAlertCommand;
import net.ripe.rpki.services.impl.email.EmailSender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UnsubscribeFromRoaAlertCommandHandlerTest {

    private static final Long TEST_CA_ID = 2L;
    private static final VersionedId TEST_VERSIONED_CA_ID = new VersionedId(TEST_CA_ID);
    private ManagedCertificateAuthority certificateAuthority;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private RoaAlertConfigurationRepository repository;

    @Mock
    private EmailSender emailSender;
    private ArgumentCaptor<String> emailCapture;

    private UnsubscribeFromRoaAlertCommandHandler subject;

    @Before
    public void setUp() {
        certificateAuthority = TestObjects.createInitialisedProdCaWithRipeResources();
        emailCapture = ArgumentCaptor.forClass(String.class);

        subject = new UnsubscribeFromRoaAlertCommandHandler(certificateAuthorityRepository, repository, emailSender);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertSame(UnsubscribeFromRoaAlertCommand.class, subject.commandType());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRoaSpecificationAndSendConfirmationEmail() {
        final String email = "joe@example.com";
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority, email,
                Arrays.asList(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH,
                        RouteValidityState.UNKNOWN), RoaAlertFrequency.DAILY);
        when(repository.findByCertificateAuthorityIdOrNull(TEST_CA_ID)).thenReturn(configuration);

        subject.handle(new UnsubscribeFromRoaAlertCommand(TEST_VERSIONED_CA_ID, email));

        verify(emailSender).sendEmail(emailCapture.capture(),
                eq(EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE.templateSubject),
                eq(EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE), isA(Map.class), isA(String.class));
        assertEquals(email, emailCapture.getValue());
    }
}
