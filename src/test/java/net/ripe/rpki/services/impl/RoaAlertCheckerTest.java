package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.bgpris.BgpRisEntryRepositoryBean;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.services.impl.background.RoaAlertBackgroundServiceDailyBeanTest;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailSenderBean;
import net.ripe.rpki.services.impl.email.EmailTokens;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;
import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class RoaAlertCheckerTest {

    private static final RoaAlertConfigurationData ALERT_SUBSCRIPTION_DATA = RoaAlertBackgroundServiceDailyBeanTest.ALERT_SUBSCRIPTION_DATA;

    private static final long CA_ID = ALERT_SUBSCRIPTION_DATA.getCertificateAuthority().getId();
    private static final ImmutableResourceSet CERTIFIED_RESOURCES = ALERT_SUBSCRIPTION_DATA.getCertificateAuthority().getResources();

    private static final RoaConfigurationData ROA_CONFIGURATION_DATA = new RoaConfigurationData(Collections.singletonList(
        new RoaConfigurationPrefixData(Asn.parse("AS65535"), IpRange.parse("127.0.0.0/8"), null)));

    private static final BgpRisEntry BGP_RIS_ENTRY_1 = new BgpRisEntry(Asn.parse("AS65535"), IpRange.parse("127.0.0.0/12"), 100);
    private static final BgpRisEntry BGP_RIS_ENTRY_1_1 = new BgpRisEntry(Asn.parse("AS65535"), IpRange.parse("127.16.0.0/12"), 100);
    private static final BgpRisEntry BGP_RIS_ENTRY_2 = new BgpRisEntry(Asn.parse("AS11111"), IpRange.parse("127.0.0.0/8"), 100);
    private static final BgpRisEntry BGP_RIS_ENTRY_2_1 = new BgpRisEntry(Asn.parse("AS22222"), IpRange.parse("127.0.0.0/8"), 100);
    private static final BgpRisEntry BGP_RIS_ENTRY_2_2 = new BgpRisEntry(Asn.parse("AS33333"), IpRange.parse("127.0.0.0/8"), 100);

    @Mock
    private RoaViewService roaService;

    @Mock
    private BgpRisEntryRepositoryBean bgpRisEntryRepository;

    @Mock
    private JavaMailSenderImpl mailSender;

    @Mock
    private InternalNamePresenter internalNamePresenter;

    private RoaAlertChecker subject;

    private final String rpkiDashboardUri = "https://dashboard.rpki.ripe.net";
    private final String authUnsubscribeUri = "http://access.ripe.net/?originalUrl=";
    private final String apiUnsubscribeUri = "https://my.ripe.net/api/email/unsubscribe-alerts";
    private final String unsubscribeSecret = UUID.randomUUID().toString();

    private final EmailTokens emailTokens = new EmailTokens(unsubscribeSecret, authUnsubscribeUri, apiUnsubscribeUri);

    @Before
    public void setup() {
        EmailSender emailSenderBean = new EmailSenderBean(mailSender, emailTokens, rpkiDashboardUri);
        subject = new RoaAlertChecker(roaService, bgpRisEntryRepository, internalNamePresenter, emailSenderBean, new SimpleMeterRegistry());

        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, "junit");
    }

    @After
    public void tearDown() {
        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, Environment.LOCAL_ENV_NAME);
    }

    @Test
    public void shouldCheckRoasAgainstBgpForInvalidLength() throws Exception {
        when(internalNamePresenter.humanizeCaName(isA(X500Principal.class))).thenReturn("zz.example");
        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(ROA_CONFIGURATION_DATA);
        when(bgpRisEntryRepository.findMostSpecificOverlapping(CERTIFIED_RESOURCES)).thenReturn(Arrays.asList(BGP_RIS_ENTRY_1, BGP_RIS_ENTRY_1_1));

        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        subject.checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA);

        ArgumentCaptor<MimeMessage> capturedMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(capturedMessage.capture());

        String expected = "Dear colleague,\n" +
            "\n" +
            "This is an automated alert email about BGP announcements with your certified\n" +
            "address space for zz.example in the Resource Certification (RPKI) service.\n\n" +
            "\n- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "These are BGP announcements with your certified address space that have\n" +
            "the status \"Invalid Length\". The prefix length in the BGP announcement does\n" +
            "not match the prefix length in the corresponding ROA.\n" +
            "\n" +
            "AS Number   Prefix\n" +
            "AS65535   127.0.0.0/12\n" +
            "AS65535   127.16.0.0/12\n" +
            "\n" +
            "\n" +
            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "There are no BGP announcements for your certified address space for which the\n" +
            "alerts have been muted.\n" +
            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "You are able to fix and ignore reported issues, change your alert\n" +
            "settings, or unsubscribe by visiting " + rpkiDashboardUri + " or\n" +
            "directly using " + unsubscribeUrl() + ".\n" +
            "\n" +
            "Contact us by email at rpki@ripe.net if you have any questions.";

        assertEquals("Resource Certification (RPKI) alerts for zz.example", capturedMessage.getValue().getSubject());
        assertEquals(expected, capturedMessage.getValue().getContent());
    }

    private String unsubscribeUrl() {
        String emails1 = ALERT_SUBSCRIPTION_DATA.getSubscription().getEmails().get(0);
        var encodedEmail = EmailTokens.enc(emails1);
        var uniqueId = EmailTokens.uniqueId(ALERT_SUBSCRIPTION_DATA.getCertificateAuthority().getUuid());
        var unsubscribeToken = emailTokens.createUnsubscribeToken(uniqueId, emails1);
        return authUnsubscribeUri + EmailTokens.enc(apiUnsubscribeUri + "/" + encodedEmail + "/" + unsubscribeToken);
    }

    @Test
    public void shouldCheckRoasAgainstBgpForInvalidAsn() throws Exception {
        when(internalNamePresenter.humanizeCaName(isA(X500Principal.class))).thenReturn("zz.example");
        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(ROA_CONFIGURATION_DATA);
        when(bgpRisEntryRepository.findMostSpecificOverlapping(CERTIFIED_RESOURCES)).thenReturn(Arrays.asList(BGP_RIS_ENTRY_2, BGP_RIS_ENTRY_2_1, BGP_RIS_ENTRY_2_2));

        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        subject.checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA);

        ArgumentCaptor<MimeMessage> capturedMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(capturedMessage.capture());

        String expected = "Dear colleague,\n" +
            "\n" +
            "This is an automated alert email about BGP announcements with your certified\n" +
            "address space for zz.example in the Resource Certification (RPKI) service.\n" +
            "\n- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "These are BGP announcements with your certified address space that have\n" +
            "the status \"Invalid ASN\". Since they are being originated from an unauthorised\n" +
            "AS, this may be an indicator that a hijack could be ongoing. If these are\n" +
            "legitimate announcements, you should authorise them by creating a ROA and\n" +
            "changing their status to \"Valid\".\n" +
            "\n" +
            "AS Number   Prefix\n" +
            "AS11111   127.0.0.0/8\n" +
            "AS22222   127.0.0.0/8\n" +
            "AS33333   127.0.0.0/8\n" +
            "\n" +
            "\n" +
            "\n" +
            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "There are no BGP announcements for your certified address space for which the\n" +
            "alerts have been muted.\n" +
            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "You are able to fix and ignore reported issues, change your alert\n" +
            "settings, or unsubscribe by visiting " + rpkiDashboardUri + " or\n" +
            "directly using " + unsubscribeUrl() + ".\n" +
            "\n" +
            "Contact us by email at rpki@ripe.net if you have any questions.";

        assertEquals("Resource Certification (RPKI) alerts for zz.example", capturedMessage.getValue().getSubject());
        assertEquals(expected, capturedMessage.getValue().getContent());
    }

    @Test
    public void should_not_alert_on_ignored_announcements() {
        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(ROA_CONFIGURATION_DATA);
        when(bgpRisEntryRepository.findMostSpecificOverlapping(CERTIFIED_RESOURCES)).thenReturn(Collections.singleton(BGP_RIS_ENTRY_1));

        subject.checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA.withIgnoredAnnouncements(Collections.singleton(new AnnouncedRoute(Asn.parse("AS65535"), IpRange.parse("127.0.0.0/12")))));

        verify(mailSender, never()).send(isA(SimpleMailMessage.class));
    }

    @Test
    public void shouldListIgnoredAnnouncementsInEmail() throws Exception {
        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(ROA_CONFIGURATION_DATA);
        when(bgpRisEntryRepository.findMostSpecificOverlapping(CERTIFIED_RESOURCES)).thenReturn(Collections.singleton(BGP_RIS_ENTRY_1));

        subject.checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA.withIgnoredAnnouncements(
                Collections.singleton(new AnnouncedRoute(Asn.parse("AS65535"), IpRange.parse("127.0.0.0/12")))));

        verify(mailSender, never()).send(isA(SimpleMailMessage.class));

        when(internalNamePresenter.humanizeCaName(isA(X500Principal.class))).thenReturn("zz.example");
        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(ROA_CONFIGURATION_DATA);
        when(bgpRisEntryRepository.findMostSpecificOverlapping(CERTIFIED_RESOURCES)).thenReturn(Collections.singleton(BGP_RIS_ENTRY_2));


        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        subject.checkAndSendRoaAlertEmailToSubscription(ALERT_SUBSCRIPTION_DATA.withIgnoredAnnouncements(
                Collections.singleton(new AnnouncedRoute(Asn.parse("AS12345"), IpRange.parse("127.0.0.0/12")))));

        ArgumentCaptor<MimeMessage> capturedMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(capturedMessage.capture());

        String expected = "Dear colleague,\n" +
            "\n" +
            "This is an automated alert email about BGP announcements with your certified\n" +
            "address space for zz.example in the Resource Certification (RPKI) service.\n" +
            "\n- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "These are BGP announcements with your certified address space that have\n" +
            "the status \"Invalid ASN\". Since they are being originated from an unauthorised\n" +
            "AS, this may be an indicator that a hijack could be ongoing. If these are\n" +
            "legitimate announcements, you should authorise them by creating a ROA and\n" +
            "changing their status to \"Valid\".\n" +
            "\n" +
            "AS Number   Prefix\n" +
            "AS11111   127.0.0.0/8\n" +
            "\n" +
            "\n" +
            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "There are BGP announcements for your certified address space for which alerts\n" +
            "(such as this email) are muted. They are listed here as a reminder.\n" +
            "\n" +
            "AS Number   Prefix\n" +
            "AS12345   127.0.0.0/12\n" +
            "\n" +
            "\n- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n" +
            "\n" +
            "You are able to fix and ignore reported issues, change your alert\n" +
            "settings, or unsubscribe by visiting " + rpkiDashboardUri + " or\n" +
            "directly using " + unsubscribeUrl() + ".\n" +
            "\n" +
            "Contact us by email at rpki@ripe.net if you have any questions.";

        assertEquals("Resource Certification (RPKI) alerts for zz.example", capturedMessage.getValue().getSubject());
        assertEquals(expected, capturedMessage.getValue().getContent());
    }
}
