package net.ripe.rpki.rest.service;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import org.hamcrest.core.StringContains;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@AutoConfigureWebMvc
@Transactional
public class UpstreamCaServiceTest extends CertificationDomainTestCase {

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void before() {
        clearDatabase();
    }

    @Test
    public void shouldGetRequestWithoutAllResourcesCA() throws Exception {
        mockMvc.perform(Rest.post("/api/upstream/request"))
            .andExpect(status().is(500))
            .andExpect(content().string(new StringContains("\"error\":\"All Resources CA doesn't exist\"")));
    }

    @Test
    public void shouldGetRequestWithAllResourcesCA() throws Exception {
        commandService.execute(new CreateAllResourcesCertificateAuthorityCommand(commandService.getNextId()));
        mockMvc.perform(Rest.post("/api/upstream/request"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_XML))
            .andExpect(content().string(new StringContains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<requests.TrustAnchorRequest>")));
    }

    @Test
    public void shouldUploadEmptyResponse() throws Exception {
        RequestBuilder req =
            Rest.authenticated(
                Rest.withUserId(
                    Rest.multipart(
                        "/api/upstream/response",
                        "file", "<xml></xml>".getBytes(StandardCharsets.UTF_8)
                    )));

        mockMvc.perform(req)
            .andExpect(status().is(500))
            .andExpect(content().string(new StringContains("\"error\":\"TrustAnchorResponse element not found\"")));
    }

}