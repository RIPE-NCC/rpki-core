package net.ripe.rpki.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.NonNull;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.AdminDeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.DelegatedCa;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
@ExtendWith(MockitoExtension.class)
class RevokeDelegatedCaControllerTest extends SpringWebControllerTestCase {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryConfiguration repositoryConfiguration;

    @Mock
    private ActiveNodeService activeNodeService;

    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @Mock
    private CommandService commandService;

    @NonNull
    @Override
    protected RevokeDelegatedCaController createSubjectController() {
        return new RevokeDelegatedCaController(
                certificateAuthorityViewService,
                commandService,
                repositoryConfiguration,
                activeNodeService,
                new GitProperties(new Properties()),
                ""
        );
    }

    @BeforeEach
    void setUp() {
        lenient().when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://example.com/rpki/repository"));
        lenient().when(activeNodeService.getActiveNodeName()).thenReturn("active-node");
    }

    @Test
    void should_show_revoke_delegated_ca_page() throws Exception {
        MvcResult result = mockMvc.perform(get(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel())
                .containsEntry("searchSubmitted", false)
                .containsEntry("caName", "")
                .containsKey("foundDelegatedCa");
        verify(certificateAuthorityViewService, never()).findDelegatedCas();
    }

    @Test
    void should_search_delegated_cas_by_name() throws Exception {
        when(certificateAuthorityViewService.findDelegatedCas()).thenReturn(List.of(
                new DelegatedCa("CN=example", Optional.of("key-1"), Optional.empty()),
                new DelegatedCa("CN=other", Optional.of("key-2"), Optional.empty())
        ));

        MvcResult result = mockMvc.perform(get(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "CN=example")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel())
                .containsEntry("searchSubmitted", true)
                .containsEntry("caName", "CN=example");

        @SuppressWarnings("unchecked")
        Optional<DelegatedCa> foundDelegatedCa = (Optional<DelegatedCa>) result.getModelAndView().getModel().get("foundDelegatedCa");
        assertThat(foundDelegatedCa).isPresent();
        assertThat(foundDelegatedCa.orElseThrow().caName()).isEqualTo("CN=example");
        verify(certificateAuthorityViewService).findDelegatedCas();
    }

    @Test
    void should_revoke_non_hosted_ca_successfully() throws Exception {
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);
        X500Principal principal = new X500Principal("CN=example");
        when(ca.getType()).thenReturn(CertificateAuthorityType.NONHOSTED);
        when(ca.getVersionedId()).thenReturn(new VersionedId(1L));
        when(ca.getName()).thenReturn(principal);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(ca);

        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "CN=example")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(RevokeDelegatedCaController.REVOKE_DELEGATED_CA);
        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("success", "Delegated CA 'CN=example' has been revoked.");

        ArgumentCaptor<AdminDeleteCertificateAuthorityCommand> commandCaptor = ArgumentCaptor.forClass(AdminDeleteCertificateAuthorityCommand.class);
        verify(commandService).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getCertificateAuthorityVersionedId()).isEqualTo(new VersionedId(1L));
    }

    @Test
    void should_return_error_when_ca_is_not_delegated() throws Exception {
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);
        when(ca.getType()).thenReturn(CertificateAuthorityType.HOSTED);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(ca);

        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "CN=example")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(RevokeDelegatedCaController.REVOKE_DELEGATED_CA);
        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("error", "CA 'CN=example' is not a delegated CA.");
        verify(commandService, never()).execute(any());
    }

    @Test
    void should_return_error_on_invalid_ca_name_format() throws Exception {
        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "@@invalid@@")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(RevokeDelegatedCaController.REVOKE_DELEGATED_CA);
        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("error", "Invalid CA name format: Invalid CA name: @@invalid@@");
        verify(commandService, never()).execute(any());
    }

    @Test
    void should_return_error_when_revoke_fails() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class)))
                .thenThrow(new RuntimeException("boom"));

        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "CN=example")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(RevokeDelegatedCaController.REVOKE_DELEGATED_CA);
        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("error", "Failed to revoke CA 'CN=example': boom");
        verify(commandService, never()).execute(any());
    }

    @Test
    void should_return_bad_request_when_no_ca_selected() throws Exception {
        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verify(commandService, never()).execute(any());
    }

    @Test
    void should_return_error_when_ca_does_not_exist() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(null);

        MvcResult result = mockMvc.perform(post(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
                .param("caName", "CN=missing")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(RevokeDelegatedCaController.REVOKE_DELEGATED_CA);
        assertThat((Map<String, Object>) result.getFlashMap()).containsEntry("error", "CA 'CN=missing' doesn't exist.");
        verify(commandService, never()).execute(any());
    }

    @Test
    void should_log_user_who_revoked_delegated_ca() {
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);
        X500Principal principal = new X500Principal("CN=123");
        when(ca.getType()).thenReturn(CertificateAuthorityType.NONHOSTED);
        when(ca.getVersionedId()).thenReturn(new VersionedId(1L));
        when(ca.getName()).thenReturn(principal);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(ca);

        Logger logger = (Logger) LoggerFactory.getLogger(RevokeDelegatedCaController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            var user = new BaseController.UserData("user-666", "Fernando", "festeban@example.net");

            createSubjectController().revoke("CN=123", new RedirectAttributesModelMap(), user);
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("User Fernando revoked delegated (non-hosted) CA: CN=123"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
