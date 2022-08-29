package net.ripe.rpki.rest.service.monitoring;

import lombok.Setter;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.domain.aspa.AspaProviderAsn;
import net.ripe.rpki.rest.service.Rest;
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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.hasSize;
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
        when(aspaConfigurationRepository.findAll()).thenReturn(Arrays.asList(
            new AspaConfiguration(mock(ManagedCertificateAuthority.class), BigInteger.valueOf(1L), Collections.emptySet()),
            new AspaConfiguration(mock(ManagedCertificateAuthority.class), BigInteger.valueOf(2L), new HashSet<>(
                Arrays.asList(
                    new AspaProviderAsn(BigInteger.valueOf(10), IpResourceType.IPv4),
                    new AspaProviderAsn(BigInteger.valueOf(10), IpResourceType.IPv6)
                )))
        ));

        mockMvc.perform(Rest.get("/api/monitoring/aspa-configs"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.aspas[0].customerAsn").value("AS1"))
            .andExpect(jsonPath("$.aspas[0].providerAsns", hasSize(0)))
            .andExpect(jsonPath("$.aspas[1].customerAsn").value("AS2"))
            .andExpect(jsonPath("$.aspas[1].providerAsns", hasSize(2)))
            .andExpect(jsonPath("$.aspas[1].providerAsns[0].asn").value("AS10"))
            .andExpect(jsonPath("$.aspas[1].providerAsns[0].prefixType").value("IPv6"))
            .andExpect(jsonPath("$.aspas[1].providerAsns[1].asn").value("AS10"))
            .andExpect(jsonPath("$.aspas[1].providerAsns[1].prefixType").value("IPv4"))
            .andExpect(jsonPath("$.metadata").isMap())
            .andExpect(jsonPath("$.aspas", hasSize(2)));
    }
}
