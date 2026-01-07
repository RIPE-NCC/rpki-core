package net.ripe.rpki.rest.service;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class ProductionCaServiceTest extends CertificationDomainTestCase {

    @MockitoBean
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @MockitoBean
    private CommandService commandService;
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void should_create_intermediate_cas() throws Exception {
        when(commandService.getNextId()).thenReturn(new VersionedId(100));
        final CertificateAuthorityData productionCa = new ManagedCertificateAuthorityData(new VersionedId(1L),
            repositoryConfiguration.getProductionCaPrincipal(), UUID.randomUUID(), 2L, CertificateAuthorityType.ROOT,
            ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());
        when(certificateAuthorityViewService.findCertificateAuthorityByName(productionCa.getName())).thenReturn(productionCa);

        mockMvc
            .perform(Rest.post("/prod/ca/add-intermediate-cas").contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isNoContent());

        verify(commandService, times(1)).execute(isA(CreateIntermediateCertificateAuthorityCommand.class));
    }

}
