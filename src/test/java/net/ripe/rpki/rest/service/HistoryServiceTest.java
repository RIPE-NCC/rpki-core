package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.CaHistoryService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.List;

import static java.util.Arrays.asList;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class HistoryServiceTest {
    @MockitoBean
    private CaHistoryService caHistoryService;
    @MockitoBean
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        reset(certificateAuthorityViewService);
    }

    @Test
    public void shouldGetResources() throws Exception {
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(ca);

        List<CertificateAuthorityHistoryItem> history = asList(
                new ProvisioningAuditData(
                        DateTime.parse("2013-04-24T11:43:07.789Z"),
                        "principal 2",
                        "Some message"
                ),
                new CommandAuditData(
                        DateTime.parse("2012-11-12T23:59:21.123Z"),
                        new VersionedId(1L),
                        "principal 1",
                        "Some command type",
                        CertificateAuthorityCommandGroup.USER,
                        "Some cool command",
                        ""
                )
        );

        when(caHistoryService.getHistoryItems(ca)).thenReturn(history);

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].length()").value("3"))
                .andExpect(jsonPath("$.[0].time").value("2013-04-24T11:43:07.789Z"))
                .andExpect(jsonPath("$.[0].principal").value("principal 2"))
                .andExpect(jsonPath("$.[0].summary").value("Some message"))
                .andExpect(jsonPath("$.[1].length()").value("6"))
                .andExpect(jsonPath("$.[1].time").value("2012-11-12T23:59:21.123Z"))
                .andExpect(jsonPath("$.[1].principal").value("principal 1"))
                .andExpect(jsonPath("$.[1].commandType").value("Some command type"))
                .andExpect(jsonPath("$.[1].commandGroup").value("USER"))
                .andExpect(jsonPath("$.[1].caId").value("1"))
                .andExpect(jsonPath("$.[1].summary").value("Some cool command"));
    }
}
