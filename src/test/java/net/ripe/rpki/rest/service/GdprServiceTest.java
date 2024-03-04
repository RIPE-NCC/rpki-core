package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.application.impl.CommandAuditServiceBean;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class GdprServiceTest {

    @MockBean
    private CommandAuditServiceBean commandAuditService;

    @MockBean
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnEmptyResult() throws Exception {
        mockMvc.perform(Rest.post("/api/public/gdpr/investigate")
                        .content("{\"emails\":[\"bad@ripe.net\",\"bad2@ripe.net\",\"bad@ripe.net\"],\"id\":\"cef5372c-ac38-4bde-863d-2e7b5b44e8c0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("3"))
                .andExpect(jsonPath("$.reports.length()").value("0"))
                .andExpect(jsonPath("$.anyMatch").value("false"));
    }

    @Test
    public void shouldReturnSubscribedEmail() throws Exception {
        var caName1 = "CN=12345";
        var ca1 = mock(CertificateAuthority.class);
        when(ca1.getName()).thenReturn(new X500Principal(caName1));

        var alert1 = new RoaAlertConfiguration(ca1, "bad@ripe.net",
                Collections.singletonList(RouteValidityState.INVALID_ASN), RoaAlertFrequency.DAILY);
        var alert2 = new RoaAlertConfiguration(ca1, "bad2@ripe.net",
                Collections.singletonList(RouteValidityState.INVALID_ASN), RoaAlertFrequency.DAILY);

        when(roaAlertConfigurationRepository.findByEmail("bad@ripe.net")).thenReturn(List.of(alert1, alert2));

        mockMvc.perform(Rest.post("/api/public/gdpr/investigate")
                        .content("{\"emails\":[\"bad@ripe.net\",\"bad2@ripe.net\",\"bad@ripe.net\"],\"id\":\"cef5372c-ac38-4bde-863d-2e7b5b44e8c0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value("2"))
                .andExpect(jsonPath("$.reports.[0].name").value("Subscription"))
                .andExpect(jsonPath("$.reports.[0].description").value("Subscribed 'bad@ripe.net' for alerts for the CA(s) " + caName1))
                .andExpect(jsonPath("$.reports.[0].occurrences").value("1"))
                .andExpect(jsonPath("$.reports.[1].name").value("Subscription"))
                .andExpect(jsonPath("$.reports.[1].description").value("Subscribed 'bad2@ripe.net' for alerts for the CA(s) " + caName1))
                .andExpect(jsonPath("$.reports.[1].occurrences").value("1"))
                .andExpect(jsonPath("$.anyMatch").value("true"))
                .andExpect(jsonPath("$.partOfRegistry").value("false"));
    }

    @Test
    public void shouldReturnHistoryEmail() throws Exception {
        Map<String, Long> history = new HashMap<>();
        history.put("RoaAlertConfiguration", 1L);
        history.put("SubscribeToRoaAlertCommand", 2L);
        history.put("UnsubscribeFromRoaAlertCommand", 3L);
        when(commandAuditService.findMentionsInSummary("bad@ripe.net")).thenReturn(history);

        mockMvc.perform(Rest.post("/api/public/gdpr/investigate")
                        .content("{\"emails\":[\"bad@ripe.net\",\"bad2@ripe.net\",\"bad@ripe.net\"],\"id\":\"cef5372c-ac38-4bde-863d-2e7b5b44e8c0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value("3"))
                .andExpect(jsonPath("$.reports.[0].name").value("UnsubscribeFromRoaAlertCommand"))
                .andExpect(jsonPath("$.reports.[0].description").value("'bad@ripe.net' found in the history of commands of type UnsubscribeFromRoaAlertCommand"))
                .andExpect(jsonPath("$.reports.[0].occurrences").value("3"))
                .andExpect(jsonPath("$.reports.[1].name").value("SubscribeToRoaAlertCommand"))
                .andExpect(jsonPath("$.reports.[1].description").value("'bad@ripe.net' found in the history of commands of type SubscribeToRoaAlertCommand"))
                .andExpect(jsonPath("$.reports.[1].occurrences").value("2"))
                .andExpect(jsonPath("$.reports.[2].name").value("RoaAlertConfiguration"))
                .andExpect(jsonPath("$.reports.[2].description").value("'bad@ripe.net' found in the history of commands of type RoaAlertConfiguration"))
                .andExpect(jsonPath("$.reports.[2].occurrences").value("1"))
                .andExpect(jsonPath("$.anyMatch").value("true"))
                .andExpect(jsonPath("$.partOfRegistry").value("true"));
    }

}