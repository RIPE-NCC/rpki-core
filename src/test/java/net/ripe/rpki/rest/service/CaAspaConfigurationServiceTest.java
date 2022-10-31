package net.ripe.rpki.rest.service;

import com.google.gson.Gson;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.AspaProviderData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.DuplicateResourceException;
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.read.AspaViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaAspaConfigurationServiceTest {
    private static final long CA_ID = 123L;
    public static final List<AspaConfigurationData> ASPA_CONFIGURATION_DATA = Collections.singletonList(
        new AspaConfigurationData(Asn.parse("AS1"), Collections.singletonList(new AspaProviderData(Asn.parse("AS5"), AspaAfiLimit.ANY)))
    );
    public static final String ASPA_CONFIGURATION_ETAG = AspaConfigurationData.entityTag(AspaConfigurationData.dataToMaps(ASPA_CONFIGURATION_DATA));
    public static final Gson GSON = new Gson();
    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private AspaViewService aspaViewService;

    @MockBean
    private CommandService commandService;

    @MockBean
    private HostedCertificateAuthorityData certificateAuthorityData;

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
        when(certificateAuthorityData.getVersionedId()).thenReturn(new VersionedId(CA_ID));
        when(aspaViewService.findAspaConfiguration(CA_ID)).thenReturn(ASPA_CONFIGURATION_DATA);
    }

    @Test
    public void shouldReturnAspaConfigurationForCa() throws Exception {
        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/aspa"))
            .andExpect(status().isOk())
            .andExpect(header().stringValues(HttpHeaders.ETAG, ASPA_CONFIGURATION_ETAG))
            .andExpect(jsonPath("$.entityTag").value(ASPA_CONFIGURATION_ETAG))
            .andExpect(jsonPath("$.aspaConfigurations", hasSize(1)))
            .andExpect(jsonPath("$.aspaConfigurations[0].customerAsn").value("AS1"))
            .andExpect(jsonPath("$.aspaConfigurations[0].providers", hasSize(1)))
            .andExpect(jsonPath("$.aspaConfigurations[0].providers[0].providerAsn").value("AS5"))
            .andExpect(jsonPath("$.aspaConfigurations[0].providers[0].afiLimit").value("ANY"));
    }

    @Test
    public void shouldUpdateAspaConfigurationForCa() throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                .content("{\"ifMatch\":" + GSON.toJson(ASPA_CONFIGURATION_ETAG) + ",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"ANY\"}]}]}")
            )
            .andExpect(status().isNoContent());

        ArgumentCaptor<UpdateAspaConfigurationCommand> commandArgumentCaptor = ArgumentCaptor.forClass(UpdateAspaConfigurationCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());
        assertThat(commandArgumentCaptor.getValue().getIfMatch()).isEqualTo(ASPA_CONFIGURATION_ETAG);
    }

    @Test
    public void shouldFailWhenAspaConfigurationIsMissingOrMalformed() throws Exception {
        assertBadRequest("{\"ifMatch\":\"etag\"}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\"}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"bad-asn\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"ANY\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{\"providerAsn\":\"AS5\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"BAD-LIMIT\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS99999999999999\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"ANY\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"FOO1\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"ANY\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",'aspaConfigurations':[{\"customerAsn\":\"FOO1\",\"providers\":[{\"providerAsn\":\"AS5\",\"afiLimit\":\"ANY\"}]}]}");
    }

    @Test
    public void shouldFailWhenEntityTagIsIncorrect() throws Exception {
        when(commandService.execute(any())).thenThrow(new EntityTagDoesNotMatchException("expected", "actual"));

        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, "actual")
                .content("{\"ifMatch\":\"actual\",\"aspaConfigurations\":[]}")
            )
            .andExpect(status().isPreconditionFailed());
    }

    @Test
    public void shouldFailWhenIfMatchConditionIsMissing() throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .content("{\"aspaConfigurations\":[]}")
            )
            .andExpect(status().isPreconditionRequired());
    }

    @Test
    public void shouldFailWhenDuplicateAsnsAreConfigured() throws Exception {
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[]},{\"customerAsn\":\"AS1\",\"providers\":[]}]}");
    }

    @Test
    public void shouldFailWhenProvidersIsEmpty() throws Exception {
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":null}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[]}]}");
    }

    @Test
    public void shouldFailWhenProviderInformationIsMissing() throws Exception {
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{\"providerAsn\":\"AS123\"}]}]}");
        assertBadRequest("{\"ifMatch\":\"etag\",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[{\"afiLimit\":\"ANY\"}]}]}");
    }

    private void assertBadRequest(String content) throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                .content(content)
            )
            .andExpect(status().isBadRequest());
    }

}
