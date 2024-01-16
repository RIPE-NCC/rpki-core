package net.ripe.rpki.rest.service;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaAspaConfigurationServiceTest {
    private static final long CA_ID = 123L;
    public static final List<AspaConfigurationData> ASPA_CONFIGURATION_DATA = Collections.singletonList(
        new AspaConfigurationData(Asn.parse("AS1"), List.of(Asn.parse("AS5")))
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
                .andDo(res -> log.info(res.getResponse().getContentAsString()));
    }

    @Test
    public void shouldUpdateAspaConfigurationForCa_with_provider() throws Exception {
        var nextAs = new Random().nextInt(2048) + 1024;

        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                .content("{\"ifMatch\":" + GSON.toJson(ASPA_CONFIGURATION_ETAG) + ",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[\"AS" + nextAs + "\"]}]}")
            )
                .andDo(res -> log.info(res.getResponse().getContentAsString()))
            .andExpect(status().isNoContent());

        ArgumentCaptor<UpdateAspaConfigurationCommand> commandArgumentCaptor = ArgumentCaptor.forClass(UpdateAspaConfigurationCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());
        assertThat(commandArgumentCaptor.getValue().getIfMatch()).isEqualTo(ASPA_CONFIGURATION_ETAG);
    }

    @Test
    public void shouldUpdateAspaConfigurationForCa_with_multiple_provider() throws Exception {
        var providers = IntStream.range(1000, 1100)
                .mapToObj(i -> "AS"+i)
                .collect(Collectors.toList());

        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                        .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                        .content("{\"ifMatch\":" + GSON.toJson(ASPA_CONFIGURATION_ETAG) + ",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\": " + GSON.toJson(providers) + "}]}")
                )
                .andDo(res -> log.info(res.getResponse().getContentAsString()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UpdateAspaConfigurationCommand> commandArgumentCaptor = ArgumentCaptor.forClass(UpdateAspaConfigurationCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());
        assertThat(commandArgumentCaptor.getValue().getIfMatch()).isEqualTo(ASPA_CONFIGURATION_ETAG);
    }

    @Test
    public void shouldUpdateAspaConfigurationForCa_no_providers() throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                        .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                        .content("{\"ifMatch\":" + GSON.toJson(ASPA_CONFIGURATION_ETAG) + ",\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":[]}]}")
                )
                .andDo(res -> log.info(res.getResponse().getContentAsString()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UpdateAspaConfigurationCommand> commandArgumentCaptor = ArgumentCaptor.forClass(UpdateAspaConfigurationCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());
        assertThat(commandArgumentCaptor.getValue().getIfMatch()).isEqualTo(ASPA_CONFIGURATION_ETAG);
    }

    @Test
    public void shouldFailWhenAspaConfigurationIsMissingOrMalformed() throws Exception {
        // etag mismatch
        assertBadRequest("{\"ifMatch\":\"etag\"}");
        // Other cases do not send an etag
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"AS1\"}]}");
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"bad-asn\",\"providers\":[\"AS5\"]}]}");
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"AS99999999999999\",\"providers\":[\"AS5\"]}]}");
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"FOO1\",\"providers\":[\"AS5\"]}]}");
        assertBadRequest("{'aspaConfigurations':[{\"customerAsn\":\"FOO1\",\"providers\":[\"AS5\"]}]}");
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
    public void shouldFailWhenIfMatchHeaderAndFieldDoNotMatch() throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, "header-value")
                .content("{\"ifMatch\":\"field-value\",\"aspaConfigurations\":[]}")
            )
            .andExpect(status().isBadRequest());
    }
    @Test
    public void shouldFailWhenProvidersIsEmpty() throws Exception {
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"AS1\",\"providers\":null}]}");
    }

    @Test
    public void shouldFailWhenProviderInformationIsMissing() throws Exception {
        assertBadRequest("{\"aspaConfigurations\":[{\"customerAsn\":\"AS1\"}]}");
    }

    private void assertBadRequest(String content) throws Exception {
        mockMvc.perform(Rest.put(API_URL_PREFIX + "/123/aspa")
                .header(HttpHeaders.IF_MATCH, ASPA_CONFIGURATION_ETAG)
                .content(content)
            )
            .andDo(res -> log.info(res.getResponse().getContentAsString()))
            .andExpect(status().isBadRequest());
    }
}
