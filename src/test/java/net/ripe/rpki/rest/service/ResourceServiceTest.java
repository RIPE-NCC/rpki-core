package net.ripe.rpki.rest.service;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Before;
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

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class ResourceServiceTest {

    public static final long CA_ID = 456L;

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    private CertificateAuthorityData certificateAuthorityData = mock(CertificateAuthorityData.class);

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
    }

    @Test
    public void shouldGetResources() throws Exception {

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/resources"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("127.0.0.1")))
                .andExpect(content().string(containsString("::1")));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/ORG-1/resources"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("127.0.0.1")))
                .andExpect(content().string(containsString("::1")));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/blabla/resources"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldInvalidateIllegalPrefix() throws Exception {

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/resources/validate-prefix/{prefix}", "192.168.0.0-192.168.12.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("3"))
                .andExpect(jsonPath("$.status").value("invalid"))
                .andExpect(jsonPath("$.type").value("syntax"))
                .andExpect(jsonPath("$.message").value("192.168.0.0-192.168.12.0 is not a legal prefix"));
    }

    @Test
    public void shouldInvalidateNotOwnedPrefix() throws Exception {

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/resources/validate-prefix/{prefix}", "192.168.0.0/16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("3"))
                .andExpect(jsonPath("$.status").value("invalid"))
                .andExpect(jsonPath("$.type").value("ownership"))
                .andExpect(jsonPath("$.message").value("You are not a holder of the prefix 192.168.0.0/16"));
    }

    @Test
    public void shouldValidatePrefixWhenCAOwnsLargerPrefix() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.18.0.0/15"), Ipv6Address.parse("::1"));
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/resources/validate-prefix/{prefix}", "192.18.0.0/16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.status").value("valid"));
    }

}
