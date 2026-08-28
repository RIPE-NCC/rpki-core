package net.ripe.rpki.web;

import lombok.NonNull;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
@ExtendWith(MockitoExtension.class)
class HealthCheckControllerTest extends SpringWebControllerTestCase {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryConfiguration repositoryConfiguration;
    @Mock
    private ActiveNodeService activeNodeService;

    private final Health.Check importantServiceCheck =
            new Health.Check("Some important service") {
                public Health.Status check() {
                    return Health.warning("not available");
                }
            };

    @NonNull
    @Override
    protected HealthCheckController createSubjectController() {
        return new HealthCheckController(
                repositoryConfiguration,
                activeNodeService,
                List.of(importantServiceCheck),
                new GitProperties(new Properties())
        );
    }

    @BeforeEach
    void setUp() {
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://example.com/rpki/repository"));
        when(activeNodeService.getActiveNodeName()).thenReturn("active-node");
    }

    @Test
    void should_render_healthChecks() throws Exception {
        MvcResult result = mockMvc.perform(get(HealthCheckController.HEALTH_CHECK)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getResponse().getContentAsString())
                .contains("<td>Some important service</td>")
                .contains("WARNING")
                .contains("<td>not available</td>");
    }

}
