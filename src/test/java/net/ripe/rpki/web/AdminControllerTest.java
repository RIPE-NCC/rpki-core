package net.ripe.rpki.web;

import lombok.NonNull;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class AdminControllerTest extends SpringWebControllerTestCase {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryConfiguration repositoryConfiguration;
    @Mock
    private ActiveNodeService activeNodeService;
    @Mock
    private BackgroundService backgroundService;
    @Mock
    private BackgroundServices backgroundServices;
    @Mock
    private ProvisioningIdentityViewService provisioningIdentityViewService;

    @NonNull
    @Override
    protected AdminController createSubjectController() {
        Map<String, BackgroundService> backgroundServiceMap = Collections.singletonMap("backgroundService", backgroundService);
        return new AdminController(repositoryConfiguration, activeNodeService, backgroundServiceMap, backgroundServices, provisioningIdentityViewService);
    }

    @Before
    public void setUp() {
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://example.com/rpki/repository"));
        when(activeNodeService.getActiveNodeName()).thenReturn("active-node");
        when(backgroundService.getName()).thenReturn("mock background service");
    }

    @Test
    public void should_show_index() throws Exception {
        when(provisioningIdentityViewService.findProvisioningIdentityMaterial()).thenReturn(ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT );

        MvcResult result = mockMvc.perform(get(BaseController.ADMIN_HOME)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model).extractingByKey("activeNodeForm").isInstanceOfSatisfying(
            BaseController.ActiveNodeForm.class,
            form -> assertThat(form.name).isEqualTo("active-node")
        );
        assertThat(result.getResponse().getContentAsString())
            .contains("<input type=\"text\" required name=\"name\" value=\"active-node\" />")
            .contains("<td>mock background service</td>")
            .contains("(created at").contains("valid until");  // Provisioning identity certificate
    }

    @Test
    public void should_activate_node() throws Exception {
        MvcResult result = mockMvc.perform(post(BaseController.ADMIN_HOME + "/activate-node").param("name", "updated-node")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        verify(activeNodeService).setActiveNodeName("updated-node");
    }

    @Test
    public void should_fail_to_activate_node_with_invalid_node_name() throws Exception {
        MvcResult result = mockMvc.perform(post(BaseController.ADMIN_HOME + "/activate-node").param("name", "@@invalid-node-name@@")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verify(activeNodeService, never()).setActiveNodeName(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_run_background_service() throws Exception {
        MvcResult result = mockMvc.perform(post(BaseController.ADMIN_HOME + "/services/{id}?batchSize=42&forceUpdate=true", "backgroundService")).andReturn();

        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("success", "Scheduled service 'mock background service' for execution");
        ArgumentCaptor<Map<String, String>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(backgroundServices).trigger(eq("backgroundService"), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue())
            .hasSize(2)
            .containsEntry("batchSize", "42")
            .containsEntry("forceUpdate", "true");
    }

    @Test
    public void should_fail_to_run_unknown_background_service() throws Exception {
        MvcResult result = mockMvc.perform(post(BaseController.ADMIN_HOME + "/services/{id}", "foo")).andReturn();

        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("error", "Service not found");
        verifyNoInteractions(backgroundServices);
    }

    @Test
    public void should_download_provisioning_identity_certificate() throws Exception {
        ProvisioningIdentityCertificate testIdentityCert = ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT;
        when(provisioningIdentityViewService.findProvisioningIdentityMaterial()).thenReturn(testIdentityCert);

        MvcResult result = mockMvc.perform(get(BaseController.ADMIN_HOME + "/provisioning-identity-certificate.cer")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(testIdentityCert.getEncoded());
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pkix-cert");
    }

    @Test
    public void should_return_not_found_without_provisioning_identity_certificate() throws Exception {
        MvcResult result = mockMvc.perform(get(BaseController.ADMIN_HOME + "/provisioning-identity-certificate.cer")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
