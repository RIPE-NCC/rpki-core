package net.ripe.rpki.services.impl.email;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;
import javax.security.auth.x500.X500Principal;
import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailSenderBeanTest {

    @Mock
    private JavaMailSenderImpl mailSender;
    private ArgumentCaptor<MimeMessage> messageCapture;
    private EmailSenderBean subject;

    private final String rpkiDashboardUri = "http://localhost/unit-testing";
    private final String authUnsubscribeUri = "http://localhost/?originalUrl=";
    private final String apiUnsubscribeUri = "http://localhost/api/rpki/unsubscribe-alerts";
    private final String uniqueId = "12345678";

    @BeforeEach
    public void setUp() {
        messageCapture = ArgumentCaptor.forClass(MimeMessage.class);
        var emails = new EmailTokens("secret", authUnsubscribeUri, apiUnsubscribeUri);
        subject = new EmailSenderBean(mailSender, emails, rpkiDashboardUri);
        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, "junit");
    }

    @AfterAll
    public static void tearDown() {
        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, Environment.LOCAL_ENV_NAME);
    }

    @Test
    void shouldSendEmail() throws Exception {
        String emailTo = "email@example.com";
        var template = EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE;

        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        subject.sendEmail(emailTo, template.templateSubject, template, Collections.singletonMap("field", "value"), uniqueId);

        verify(mailSender).send(messageCapture.capture());
        assertThat(messageCapture.getValue().getAllRecipients()[0].toString()).isEqualTo(emailTo);
        assertThat(messageCapture.getValue().getSubject()).isEqualTo(template.templateSubject);
        assertThat((String)messageCapture.getValue().getContent()).hasSizeGreaterThan(100);
    }

    @Test
    void shouldRenderAllTemplates() throws Exception {
        for (var template : List.of(EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY, EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY)) {
            when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
            subject.sendEmail("user@example.org", template.templateSubject, template, variablesFor(template), uniqueId);
            verify(mailSender).send(messageCapture.capture());
            String content = (String) messageCapture.getValue().getContent();
            assertThat(content).isNotBlank();
            assertThat(content).contains(rpkiDashboardUri);
            assertThat(content).contains(authUnsubscribeUri);
            reset(mailSender);
        }
    }

    private Map<String, Object> variablesFor(EmailSender.EmailTemplates template) {
        if (template == EmailSender.EmailTemplates.ROA_ALERT) {
            var route = new AnnouncedRoute(
                    Asn.parse("AS0"),
                    IpRange.parse("10.0.0.0/8")
            );
            var ca = new HostedCertificateAuthorityData(
                    new VersionedId(42L),
                    new X500Principal("CN=org.example"),
                    UUID.randomUUID(),
                    new SecureRandom().nextLong(),
                    ImmutableResourceSet.empty(),
                    List.of()
            );
            var configuration = new RoaAlertConfigurationData(
                    ca,
                    new RoaAlertSubscriptionData("user@example.org", List.of(RouteValidityState.values()), RoaAlertFrequency.DAILY)
            );
            return Map.of(
                    "humanizedCaName", RandomStringUtils.randomAlphabetic(12),
                    "ignoredAlerts", Set.of(route),
                    "invalidAsns", List.of(route),
                    "invalidLengths", List.of(route),
                    "unknowns", List.of(route),
                    "subscription", configuration
            );
        }
        return Map.of();
    }
}
