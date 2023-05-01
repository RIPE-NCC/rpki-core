package net.ripe.rpki.web;

import lombok.NonNull;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


@RunWith(MockitoJUnitRunner.class)
public class UpstreamCaControllerTest extends SpringWebControllerTestCase {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryConfiguration repositoryConfiguration;
    @Mock
    private ActiveNodeService activeNodeService;

    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Mock
    private CommandService commandService;
    @Mock
    private AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean;
    private ManagedCertificateAuthorityData aca;

    @NonNull
    @Override
    protected UpstreamCaController createSubjectController() {
        return new UpstreamCaController(repositoryConfiguration, activeNodeService,
            certificateAuthorityViewService, commandService, allCaCertificateUpdateServiceBean, Collections.emptyMap(),  new GitProperties(new Properties()));
    }

    @Before
    public void setUp() {
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://example.com/rpki/repository"));
        when(activeNodeService.getActiveNodeName()).thenReturn("active-node");

        aca = mock(ManagedCertificateAuthorityData.class);
        when(aca.getVersionedId()).thenReturn(new VersionedId(1));
        when(aca.getType()).thenReturn(CertificateAuthorityType.ALL_RESOURCES);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any())).thenReturn(aca);
    }

    @Test
    public void should_show_upstream_ca_no_all_resources_ca() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any())).thenReturn(null);

        MvcResult result = mockMvc.perform(get("/admin/upstream-ca")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getResponse().getContentAsString()).contains("All resources CA does not exist");
    }

    @Test
    public void should_show_upstream_no_sign_request() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/upstream-ca")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model).extractingByKey("pageType").isEqualTo("create-request");

        assertThat(result.getResponse().getContentAsString()).contains("<td>Generate signing request</td>");
    }

    @Test
    public void should_show_upstream_sign_request_exists() throws Exception {
        TrustAnchorRequest taRequest = mock(TrustAnchorRequest.class);
        when(aca.getTrustAnchorRequest()).thenReturn(taRequest);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any())).thenReturn(aca);

        MvcResult result = mockMvc.perform(get("/admin/upstream-ca")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model).extractingByKey("pageType").isEqualTo("download-request");
        assertThat(model).extractingByKey("requestFileName").isEqualTo("request-19700101-000000.xml");

        assertThat(result.getResponse().getContentAsString()).contains("<td>Download signing request");
    }

    @Test
    public void should_download_sign_request() throws Exception {
        CertificateAuthorityData aca = mock(CertificateAuthorityData.class);
        TrustAnchorRequest taRequest = mock(TrustAnchorRequest.class);
        when(aca.getTrustAnchorRequest()).thenReturn(taRequest);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any())).thenReturn(aca);

        final MvcResult result = mockMvc.perform(get("/admin/download-sign-request")).andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/xml");
        assertThat(result.getResponse().getContentAsString())
            .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<requests.TrustAnchorRequest>\n" +
                "   <creationTimestamp>0</creationTimestamp>\n" +
                "   <taRequests/>\n" +
                "</requests.TrustAnchorRequest>\n");
    }

    @Test
    public void should_NOT_download_sign_request_when_no_aca() throws Exception {
        final MvcResult result = mockMvc.perform(get("/admin/download-sign-request")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getResponse().getContentAsString()).contains("All Resources CA or signing request do not exist");
    }

    @Test
    public void should_upload_sign_response() throws Exception {
        final byte[] responseBytes = Files.readAllBytes(
            Paths.get(getClass().getClassLoader().getResource("ta/sign-response.xml").toURI()));

        final MvcResult result = mockMvc.perform(
            multipart("/admin/upload-sign-response")
                .file("response", responseBytes)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getFlashMap().get("success")).isEqualTo("Successfully uploaded response");
    }

    @Test
    public void should_create_sign_request() throws Exception {
        mockMvc.perform(post("/admin/create-sign-request")).andReturn();
        verify(commandService, times(1)).execute(isA(AllResourcesCaResourcesCommand.class));
    }

    @Test
    public void should_revoke_old_aca_key() throws Exception {
        mockMvc.perform(post("/admin/revoke-old-aca-key")).andReturn();
        verify(commandService, times(1)).execute(isA(KeyManagementRevokeOldKeysCommand.class));
    }

    @Test
    public void should_activate_pending_aca_key() throws Exception {
        mockMvc.perform(post("/admin/activate-pending-aca-key")).andReturn();
        verify(commandService, times(1)).execute(isA(KeyManagementActivatePendingKeysCommand.class));
    }

    @Test
    public void should_initiate_key_roll() throws Exception {
        mockMvc.perform(post("/admin/initiate-rolling-aca-key")).andReturn();
        verify(commandService, times(1)).execute(isA(KeyManagementInitiateRollCommand.class));
    }
}
