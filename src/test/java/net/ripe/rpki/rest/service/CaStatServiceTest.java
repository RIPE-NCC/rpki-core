package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.DelegatedCa;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaStatServiceTest {

    @MockitoBean
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

    @Test
    public void shouldReturnDelegatedCas() throws Exception {
        var now = Instant.now();
        Instant later = now.plus(1, ChronoUnit.MINUTES);
        Instant errorAt = now.plus(2, ChronoUnit.MINUTES);
        List<DelegatedCa> delegatedCas = Arrays.asList(
                new DelegatedCa("CN=11",    Optional.of("key1"), Optional.of(now),    Optional.empty(),       Optional.empty()),
                new DelegatedCa("CN=5555",  Optional.of("key2"), Optional.empty(),     Optional.empty(),       Optional.empty()),
                new DelegatedCa("O=XXX",    Optional.of("key3"), Optional.of(later),   Optional.empty(),       Optional.empty()),
                new DelegatedCa("O=ERROR",  Optional.of("key4"), Optional.of(now),     Optional.of(errorAt),   Optional.of("Connection refused")));

        when(certificateAuthorityViewService.findDelegatedCas()).thenReturn(delegatedCas);

        mockMvc.perform(Rest.get("/api/ca-stat/delegated"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.[0].caName").value("CN=11"))
                .andExpect(jsonPath("$.[0].keyIdentifier").value("key1"))
                .andExpect(jsonPath("$.[0].lastProvisionedAt").value(now.toString()))
                .andExpect(jsonPath("$.[0].lastFailedAt").doesNotExist())
                .andExpect(jsonPath("$.[0].errorReason").doesNotExist())
                .andExpect(jsonPath("$.[1].caName").value("CN=5555"))
                .andExpect(jsonPath("$.[1].keyIdentifier").value("key2"))
                .andExpect(jsonPath("$.[1].lastProvisionedAt").doesNotExist())
                .andExpect(jsonPath("$.[1].lastFailedAt").doesNotExist())
                .andExpect(jsonPath("$.[1].errorReason").doesNotExist())
                .andExpect(jsonPath("$.[2].caName").value("O=XXX"))
                .andExpect(jsonPath("$.[2].keyIdentifier").value("key3"))
                .andExpect(jsonPath("$.[2].lastProvisionedAt").value(later.toString()))
                .andExpect(jsonPath("$.[2].lastFailedAt").doesNotExist())
                .andExpect(jsonPath("$.[2].errorReason").doesNotExist())
                .andExpect(jsonPath("$.[3].caName").value("O=ERROR"))
                .andExpect(jsonPath("$.[3].keyIdentifier").value("key4"))
                .andExpect(jsonPath("$.[3].lastProvisionedAt").value(now.toString()))
                .andExpect(jsonPath("$.[3].lastFailedAt").value(errorAt.toString()))
                .andExpect(jsonPath("$.[3].errorReason").value("Connection refused"));
    }
}