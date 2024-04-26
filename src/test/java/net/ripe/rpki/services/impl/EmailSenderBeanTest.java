package net.ripe.rpki.services.impl;

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
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import javax.security.auth.x500.X500Principal;
import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class EmailSenderBeanTest {

    @Mock
    private MailSender mailSender;
    private ArgumentCaptor<SimpleMailMessage> messageCapture;
    private EmailSenderBean subject;

    @BeforeEach
    public void setUp() {
        messageCapture = ArgumentCaptor.forClass(SimpleMailMessage.class);

        subject = new EmailSenderBean(mailSender, "http://localhost/unit-testing");

        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, "junit");
    }

    @AfterAll
    public static void tearDown() {
        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, Environment.LOCAL_ENV_NAME);
    }

    @Test
    public void shouldSendEmail() {
        String emailTo = "email@example.com";
        var template = EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE;

        subject.sendEmail(emailTo, template.templateSubject, template, Collections.singletonMap("field", "value"));

        verify(mailSender).send(messageCapture.capture());
        assertThat(messageCapture.getValue().getTo()[0]).isEqualTo(emailTo);
        assertThat(messageCapture.getValue().getSubject()).isEqualTo(template.templateSubject);
        assertThat(messageCapture.getValue().getText()).hasSizeGreaterThan(100);
    }

    @Test
    public void shouldRenderAllTemplates() {
        for (var template : EmailSender.EmailTemplates.values()) {
            subject.sendEmail("user@example.org", template.templateSubject, template, variablesFor(template));
            verify(mailSender).send(messageCapture.capture());
            assertThat(messageCapture.getValue().getText()).isNotBlank();
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
