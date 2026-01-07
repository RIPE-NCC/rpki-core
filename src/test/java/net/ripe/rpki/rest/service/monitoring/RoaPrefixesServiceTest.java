package net.ripe.rpki.rest.service.monitoring;

import lombok.Setter;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.rest.service.Rest;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Setter
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RoaPrefixesServiceTest {
    @MockitoBean
    private RoaConfigurationRepository roaConfigurationRepository;

    @Autowired
    private MockMvc mockMvc;

    private static HttpHeaders ifNoneMatchHeader(MvcResult result) {
        String eTag = result.getResponse().getHeader(HttpHeaders.ETAG);

        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.IF_NONE_MATCH, eTag);
        return headers;
    }

    @Test
    public void shouldAcceptLastModifiedAndReturnArrayOtherwise() throws Exception {
        final Instant t0 = Instant.now();
        when(roaConfigurationRepository.lastModified()).thenReturn(Optional.of(t0));
        when(roaConfigurationRepository.countRoaPrefixes()).thenReturn(42);

        // Get initial eTag
        MvcResult res = mockMvc.perform(
                        Rest.get("/api/monitoring/roa-prefixes")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.roas").isArray())
                .andReturn();

        // Can be re-used for identical state
        mockMvc.perform(
                Rest.get("/api/monitoring/roa-prefixes")
                    .headers(ifNoneMatchHeader(res))
        )
                .andExpect(status().isNotModified());

        // Number of objects changes -> new request.
        when(roaConfigurationRepository.countRoaPrefixes()).thenReturn(43);

        MvcResult res2 = mockMvc.perform(
                        Rest.get("/api/monitoring/roa-prefixes")
                                .headers(ifNoneMatchHeader(res))
                )
                .andExpect(status().isOk())
                .andReturn();

        // Time of last change is updated -> new request
        when(roaConfigurationRepository.lastModified()).thenReturn(Optional.of(t0.plusSeconds(60)));

        mockMvc.perform(
                        Rest.get("/api/monitoring/roa-prefixes")
                                .headers(ifNoneMatchHeader(res2))
                )
                .andExpect(status().isOk());
    }

    @Test
    public void shouldReturnObjectsAsJsonMatchingValidatedObjectsShape() throws Exception {
        when(roaConfigurationRepository.lastModified()).thenReturn(Optional.of(Instant.now()));

        when(roaConfigurationRepository.findAllPrefixes()).thenReturn(Arrays.asList(
                    new RoaConfigurationPrefixData(Asn.parse("AS64496"), IpRange.parse("192.0.2.0/25"), 32),
                    new RoaConfigurationPrefixData(Asn.parse("AS65536"), IpRange.parse("192.0.2.128/25"), 32),
                    new RoaConfigurationPrefixData(Asn.parse("AS64496"), IpRange.parse("192.0.2.128/25"), 25),
                    new RoaConfigurationPrefixData(Asn.parse("AS65551"), IpRange.parse("2001:DB8::/32"), 33),
                    new RoaConfigurationPrefixData(Asn.parse("AS65550"), IpRange.parse("2001:DB8:ABCD::/48"), 48)
                ));

        // shape:
        // ...
        //    {
        //      "asn": "AS21409",
        //      "prefix": "213.246.49.0/24",
        //      "maxLength": 32,
        //    },
        // ...
        mockMvc.perform(Rest.get("/api/monitoring/roa-prefixes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.roas[0].asn").value("AS64496"))
                .andExpect(jsonPath("$.roas[0].prefix").value("192.0.2.0/25"))
                .andExpect(jsonPath("$.roas[0].maxLength").value("32"))
                .andExpect(jsonPath("$.metadata").isMap())
                // flattened, 3+2=5
                .andExpect(jsonPath("$.roas", hasSize(5)));
    }
}
