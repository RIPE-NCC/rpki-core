package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
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

import java.util.Arrays;
import java.util.Collection;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaStatServiceTest {

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnAllCas() throws Exception {
        Collection<CaStat> caStats = Arrays.asList(
                new CaStat("CN=11", 2, "2011-12-12 12:13:14"),
                new CaStat("O=XXX", 3, "2013-11-24 20:30:40"));
        when(certificateAuthorityViewService.getCaStats()).thenReturn(caStats);

        mockMvc.perform(Rest.get("/api/ca-stat/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.[0].caName").value("CN=11"))
                .andExpect(jsonPath("$.[0].roas").value("2"))
                .andExpect(jsonPath("$.[0].createdAt").value("2011-12-12 12:13:14"))
                .andExpect(jsonPath("$.[1].caName").value("O=XXX"))
                .andExpect(jsonPath("$.[1].roas").value("3"))
                .andExpect(jsonPath("$.[1].createdAt").value("2013-11-24 20:30:40"));
    }

    // TODO There should be more tests

}