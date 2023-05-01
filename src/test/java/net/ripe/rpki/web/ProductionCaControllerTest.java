package net.ripe.rpki.web;

import lombok.NonNull;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.services.system.CaHistoryService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class ProductionCaControllerTest extends SpringWebControllerTestCase {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Mock
    private InternalNamePresenter internalNamePresenter;
    @Mock
    private RepositoryConfiguration repositoryConfiguration;
    @Mock
    private CaHistoryService caHistoryService;
    @Mock
    private ActiveNodeService activeNodeService;


    @NonNull
    @Override
    protected ProductionCaController createSubjectController() {
        return new ProductionCaController(
            certificateAuthorityViewService,
            internalNamePresenter,
            repositoryConfiguration,
            caHistoryService,
            activeNodeService,
            new GitProperties(new Properties())
        );
    }

    @Before
    public void setUp() {
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);

        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(ca);
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://example.com/rpki/repository"));
        when(repositoryConfiguration.getLocalRepositoryDirectory()).thenReturn(new File("/tmp/test/repository"));
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL"));

        List<CertificateAuthorityHistoryItem> history = asList(
                new ProvisioningAuditData(
                        DateTime.parse("2013-04-24T11:43:07.789Z"),
                        "principal 2",
                        "Some message"
                ),
                new CommandAuditData(
                        DateTime.parse("2012-11-12T23:59:21.123Z"),
                        new VersionedId(1L),
                        "principal 1",
                        "Some command type",
                        CertificateAuthorityCommandGroup.USER,
                        "Some cool command",
                        ""
                )
        );

        when(caHistoryService.getHistoryItems(ca)).thenReturn(history);
    }

    @Test
    public void should_render_history() throws Exception {
        MvcResult result = mockMvc.perform(get(ProductionCaController.PRODUCTION_CA_HISTORY)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getResponse().getContentAsString())
                .contains("<td>2012-11-12 23:59:21</td>")
                .contains("<td>principal 1</td>")
                .contains("Some cool command");
    }
}
