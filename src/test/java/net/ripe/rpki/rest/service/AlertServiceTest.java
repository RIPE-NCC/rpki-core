package net.ripe.rpki.rest.service;

import com.google.common.collect.Sets;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.server.api.commands.UnsubscribeFromRoaAlertCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class AlertServiceTest {

    public static final long CA_ID = 456L;

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private RoaAlertConfigurationViewService roaAlertConfigurationViewService;

    @MockBean
    private CommandService commandService;

    private HostedCertificateAuthorityData certificateAuthorityData = mock(HostedCertificateAuthorityData.class);

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
        when(certificateAuthorityData.getVersionedId()).thenReturn(new VersionedId(CA_ID));
    }

    @Test
    public void shouldGetExistingAlerts() throws Exception {

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Arrays.asList("festeban@ripe.net", "bad@ripe.net"),
                Arrays.asList(RouteValidityState.INVALID_ASN, RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY);

        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/alerts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.routeValidityStates[0]").value("INVALID_ASN"))
                .andExpect(jsonPath("$.routeValidityStates[1]").value("UNKNOWN"))
                .andExpect(jsonPath("$.emails[0]").value("festeban@ripe.net"))
                .andExpect(jsonPath("$.emails[1]").value("bad@ripe.net"))
                .andExpect(jsonPath("$.frequency").value("WEEKLY"));
    }

    @Test
    public void shouldSubscribeToAlerts() throws Exception {

        when(certificateAuthorityViewService.findCertificateAuthority(CA_ID)).thenReturn(certificateAuthorityData);

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Arrays.asList("festeban@ripe.net", "bad@ripe.net"),
                Arrays.asList(RouteValidityState.INVALID_ASN, RouteValidityState.UNKNOWN), RoaAlertFrequency.DAILY);
        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        ArgumentCaptor<CertificateAuthorityCommand> commandArgument = ArgumentCaptor.forClass(CertificateAuthorityCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts",
                "{\"routeValidityStates\" : [\"INVALID_LENGTH\"], \"emails\" : [\"bad1@ripe.net\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(commandService, times(3)).execute(commandArgument.capture());
        List<CertificateAuthorityCommand> commands = commandArgument.getAllValues();


        UnsubscribeFromRoaAlertCommand unsubscribe1 = (UnsubscribeFromRoaAlertCommand) commands.get(0);
        UnsubscribeFromRoaAlertCommand unsubscribe2 = (UnsubscribeFromRoaAlertCommand) commands.get(1);
        SubscribeToRoaAlertCommand subscribe = (SubscribeToRoaAlertCommand) commands.get(2);

        assertEquals("bad1@ripe.net", subscribe.getEmail());

        assertEquals(
                Sets.newHashSet(RouteValidityState.INVALID_LENGTH),
                Sets.newHashSet(subscribe.getRouteValidityStates()));

        assertEquals(
                Sets.newHashSet("bad@ripe.net", "festeban@ripe.net"),
                Sets.newHashSet(unsubscribe1.getEmail(), unsubscribe2.getEmail()));
    }

    @Test
    public void shouldSubscribeToAlertsWhenOnlyValidityStatusChanges() throws Exception {

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Collections.singletonList("bad@ripe.net"),
                Collections.singletonList(RouteValidityState.INVALID_ASN), RoaAlertFrequency.DAILY);
        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        ArgumentCaptor<CertificateAuthorityCommand> commandArgument = ArgumentCaptor.forClass(CertificateAuthorityCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts",
                "{\"routeValidityStates\" : [\"INVALID_LENGTH\", \"INVALID_ASN\"], \"emails\" : [\"bad@ripe.net\"], \"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(commandService, times(1)).execute(commandArgument.capture());
        List<CertificateAuthorityCommand> commands = commandArgument.getAllValues();

        SubscribeToRoaAlertCommand subscribe = (SubscribeToRoaAlertCommand) commands.get(0);
        assertEquals("bad@ripe.net", subscribe.getEmail());
        assertEquals(RoaAlertFrequency.WEEKLY, subscribe.getFrequency());

        assertEquals(
                Sets.newHashSet(RouteValidityState.INVALID_LENGTH, RouteValidityState.INVALID_ASN),
                Sets.newHashSet(subscribe.getRouteValidityStates()));
    }

    @Test
    public void subscribeOnlyAdditionalEmailsWhenValidityAndFrequencyUnchanged() throws Exception {

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Arrays.asList("badweekly@ripe.net"),
                Collections.singletonList(RouteValidityState.INVALID_ASN), RoaAlertFrequency.WEEKLY);
        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        ArgumentCaptor<CertificateAuthorityCommand> commandArgument = ArgumentCaptor.forClass(CertificateAuthorityCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts",
                "{\"routeValidityStates\" : [\"INVALID_ASN\"], \"emails\" : [\"badweekly@ripe.net\",\"boyweekly@ripe.net\"], " +
                        "\"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));

        //Additional email is only boyweekly@ripe.net, badweekly@ripe.net should not be resubscribed.
        verify(commandService, times(1)).execute(commandArgument.capture());
        List<CertificateAuthorityCommand> commands = commandArgument.getAllValues();

        SubscribeToRoaAlertCommand subscribe = (SubscribeToRoaAlertCommand) commands.get(0);
        assertEquals("boyweekly@ripe.net", subscribe.getEmail());
        assertEquals(RoaAlertFrequency.WEEKLY, subscribe.getFrequency());

        assertEquals(
                Sets.newHashSet(RouteValidityState.INVALID_ASN),
                Sets.newHashSet(subscribe.getRouteValidityStates()));
    }

    @Test
    public void resubscribeEveryoneWhenFrequencyChanges() throws Exception {

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Arrays.asList("bad@ripe.net","boy@ripe.net"),
                Collections.singletonList(RouteValidityState.INVALID_ASN), RoaAlertFrequency.WEEKLY);
        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        ArgumentCaptor<CertificateAuthorityCommand> commandArgument = ArgumentCaptor.forClass(CertificateAuthorityCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts",
                "{\"routeValidityStates\" : [\"INVALID_ASN\"], \"emails\" : [\"bad@ripe.net\",\"boy@ripe.net\"], " +
                        "\"frequency\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));

        //Additional email is only boyweekly@ripe.net, badweekly@ripe.net should not be resubscribed.
        verify(commandService, times(2)).execute(commandArgument.capture());
        List<CertificateAuthorityCommand> commands = commandArgument.getAllValues();

        Set<String> emails = commands.stream().map(c -> ((SubscribeToRoaAlertCommand) c).getEmail()).collect(Collectors.toSet());
        assertTrue(emails.contains("bad@ripe.net"));
        assertTrue(emails.contains("boy@ripe.net"));

        Set<String> frequencies = commands.stream().map(c -> ((SubscribeToRoaAlertCommand) c).getFrequency().toString()).collect(Collectors.toSet());
        assertTrue(frequencies.size() == 1);
        assertTrue(frequencies.contains("DAILY"));

        Set<String> validities = commands.stream().map(c -> ((SubscribeToRoaAlertCommand) c).getRouteValidityStates().toString()).collect(Collectors.toSet());
        assertTrue(validities.size() == 1);
        assertTrue(validities.contains("[INVALID_ASN]"));

    }

    @Test
    public void shouldUnsubscribeAnyoneWhenStatusesAreEmpty() throws Exception {

        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        RoaAlertSubscriptionData roaSubscriptionData = new RoaAlertSubscriptionData(
                Arrays.asList("festeban@ripe.net", "bad@ripe.net"),
                Arrays.asList(RouteValidityState.INVALID_ASN, RouteValidityState.UNKNOWN),
                RoaAlertFrequency.DAILY);
        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(new RoaAlertConfigurationData(caData, roaSubscriptionData));

        ArgumentCaptor<CertificateAuthorityCommand> commandArgument = ArgumentCaptor.forClass(CertificateAuthorityCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts",
                "{\"routeValidityStates\" : [], \"emails\" : [\"bad1@ripe.net\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(commandService, times(2)).execute(commandArgument.capture());
        List<CertificateAuthorityCommand> commands = commandArgument.getAllValues();

        UnsubscribeFromRoaAlertCommand unsubscribe1 = (UnsubscribeFromRoaAlertCommand) commands.get(0);
        UnsubscribeFromRoaAlertCommand unsubscribe2 = (UnsubscribeFromRoaAlertCommand) commands.get(1);

        assertEquals(
                Sets.newHashSet("bad@ripe.net", "festeban@ripe.net"),
                Sets.newHashSet(unsubscribe1.getEmail(), unsubscribe2.getEmail()));
    }

    @Test
    public void shouldMuteAnnouncements() throws Exception {

        final ArgumentCaptor<UpdateRoaAlertIgnoredAnnouncedRoutesCommand> commandArgument =
                ArgumentCaptor.forClass(UpdateRoaAlertIgnoredAnnouncedRoutesCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts/suppress",
                "[{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}]"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(commandService).execute(commandArgument.capture());
        UpdateRoaAlertIgnoredAnnouncedRoutesCommand command = commandArgument.getValue();
        assertEquals(Asn.parse("AS10"), command.getAdditions().get(0).getOriginAsn());
        assertEquals(IpRange.parse("192.168.0.0/16"), command.getAdditions().get(0).getPrefix());
    }

    @Test
    public void shouldUnmuteAnnouncements() throws Exception {
        final ArgumentCaptor<UpdateRoaAlertIgnoredAnnouncedRoutesCommand> commandArgument =
                ArgumentCaptor.forClass(UpdateRoaAlertIgnoredAnnouncedRoutesCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts/unsuppress",
                "[{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}]"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(commandService).execute(commandArgument.capture());
        UpdateRoaAlertIgnoredAnnouncedRoutesCommand command = commandArgument.getValue();
        assertEquals(Asn.parse("AS10"), command.getDeletions().get(0).getOriginAsn());
        assertEquals(IpRange.parse("192.168.0.0/16"), command.getDeletions().get(0).getPrefix());
    }

    @Test
    public void shouldRejectLargeRequests2() throws Exception {
        String requestBody = IntStream
            .range(100_000, 200_000)
            .mapToObj(x -> "\"" + x + "\"")
            .collect(Collectors.joining(", ", "{\"foo\":[", "]}"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/alerts", requestBody))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(content().contentType(APPLICATION_JSON));
    }
}
