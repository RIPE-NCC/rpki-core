package net.ripe.rpki.rest.service.monitoring;

import lombok.Setter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.rest.service.Rest;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Setter
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class AspaServiceTest {
    @MockBean
    private AspaConfigurationRepository aspaConfigurationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnProperAspas() throws Exception {
        Map<Asn, AspaAfiLimit> providers = new TreeMap<>();
        providers.put(Asn.parse("AS10"), AspaAfiLimit.IPv4);
        providers.put(Asn.parse("AS11"), AspaAfiLimit.IPv6);
        providers.put(Asn.parse("AS12"), AspaAfiLimit.ANY);
        when(aspaConfigurationRepository.findAll()).thenReturn(Arrays.asList(
            new AspaConfiguration(mock(ManagedCertificateAuthority.class), Asn.parse("AS1"), Collections.emptyMap()),
            new AspaConfiguration(mock(ManagedCertificateAuthority.class), Asn.parse("AS2"), providers)
        ));

        mockMvc.perform(Rest.get("/api/monitoring/aspa-configurations"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.aspaConfigurations[0].customerAsn").value("AS1"))
            .andExpect(jsonPath("$.aspaConfigurations[0].providers", hasSize(0)))
            .andExpect(jsonPath("$.aspaConfigurations[1].customerAsn").value("AS2"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers", hasSize(3)))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[0].providerAsn").value("AS10"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[0].afiLimit").value("IPv4"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[1].providerAsn").value("AS11"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[1].afiLimit").value("IPv6"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[2].providerAsn").value("AS12"))
            .andExpect(jsonPath("$.aspaConfigurations[1].providers[2].afiLimit").value("ANY"))
            .andExpect(jsonPath("$.metadata").isMap())
            .andExpect(jsonPath("$.aspaConfigurations", hasSize(2)));
    }
}
