package net.ripe.rpki.rest.service;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponseSerializer;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.rest.exception.CaNameInvalidException;
import net.ripe.rpki.server.api.commands.DeleteNonHostedPublisherCommand;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;

@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class PublisherRepositoriesServiceTest {
    private static final NonHostedCertificateAuthorityData NON_HOSTED_CA_DATA = new NonHostedCertificateAuthorityData(
        new VersionedId(12, 1),
        new X500Principal("O=ORG-1"),
        UUID.randomUUID(),
        1L,
        null,
        ImmutableResourceSet.empty(),
        Collections.emptySet()
    );

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private CommandService commandService;

    @Autowired
    private MockMvc mockMvc;


    private static final String PUBLISHER_REQUEST_XML =
        "<ns0:publisher_request xmlns:ns0=\"http://www.hactrn.net/uris/rpki/rpki-setup/\" publisher_handle=\"Bob\" version=\"1\">\n" +
            "<ns0:publisher_bpki_ta>\n" +
            "MIIDIDCCAgigAwIBAgIBATANBgkqhkiG9w0BAQsFADApMScwJQYDVQQDEx5Cb2Ig\n" +
            "QlBLSSBSZXNvdXJjZSBUcnVzdCBBbmNob3IwHhcNMTEwNzAxMDQwNzIzWhcNMTIw\n" +
            "NjMwMDQwNzIzWjApMScwJQYDVQQDEx5Cb2IgQlBLSSBSZXNvdXJjZSBUcnVzdCBB\n" +
            "bmNob3IwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDEk1f7cVzHu3r/\n" +
            "fJ5gkBxnWMNJ1CP0kPtfP8oFOEVVH1lX0MHuFKhdKA4WCkGkeFtGb/4T/nGgsD+z\n" +
            "exZid6RR8zjHkwMLvAl0x6wdKa46XJbFu+wTSu+vlowVY9rGzH+ttv4Fj6E2Y3DG\n" +
            "983/dVNJfXl00+Ts7rlvVcn9lI5dWvzsLoUOdhD4hsyKp53k8i4HexiD+0ugPeh9\n" +
            "4PKiyZOuMjSRNQSBUA3ElqJSRZz7nWvs/j6zhwHdFa+lN56575Mc5mrwr+KePwW5\n" +
            "DLt3izYpjwKffVuxUKPTrhvnOOg5kBBv0ihync21LSLds6jusxaMYUwUElO8KQyn\n" +
            "NUAeGPd/AgMBAAGjUzBRMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFORFOC3G\n" +
            "PjYKn7V1/BJHDmZ4W7J+MB8GA1UdIwQYMBaAFORFOC3GPjYKn7V1/BJHDmZ4W7J+\n" +
            "MA0GCSqGSIb3DQEBCwUAA4IBAQBqsP4ENtWTkNdsekYB+4hO/Afq20Ho0W8lyTkM\n" +
            "JO1UFDt/dzFAmTT4uM7pmwuQfqmCYjNDWon8nsdFno4tA0is3aiq6yIMAYzBE5ub\n" +
            "bnJMxldqLoWuakco1wYa3kZFzWPwecxgJ4ZlqTPGu0Loyjibt25IE9MfixyWDw+D\n" +
            "MhyfonLLgFb5jz7A3BTE63vlTp359uDbFb1nRdyoT31s3FUBK8jF4B5pWzPiLdct\n" +
            "bOMVjYUBs8aFC3fDXyGSr/RcjE4OOZQyTkYZn8zCPUJ4KqOPAUV9u9jx2FPvOcA3\n" +
            "1BjcmhYHqott+cnK1ITOjLe9EKejRZv/7/BFsmpzm2Zbq1KA\n" +
            "</ns0:publisher_bpki_ta>\n" +
            "</ns0:publisher_request>";
    private static final String PUBLISHER_REQUEST_TAG_XML =
        "<ns0:publisher_request xmlns:ns0=\"http://www.hactrn.net/uris/rpki/rpki-setup/\" tag=\"A0001\" publisher_handle=\"Mallory\" version=\"1\">\n" +
                "<ns0:publisher_bpki_ta>\n" +
                "MIIDIDCCAgigAwIBAgIBATANBgkqhkiG9w0BAQsFADApMScwJQYDVQQDEx5Cb2Ig\n" +
                "QlBLSSBSZXNvdXJjZSBUcnVzdCBBbmNob3IwHhcNMTEwNzAxMDQwNzIzWhcNMTIw\n" +
                "NjMwMDQwNzIzWjApMScwJQYDVQQDEx5Cb2IgQlBLSSBSZXNvdXJjZSBUcnVzdCBB\n" +
                "bmNob3IwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDEk1f7cVzHu3r/\n" +
                "fJ5gkBxnWMNJ1CP0kPtfP8oFOEVVH1lX0MHuFKhdKA4WCkGkeFtGb/4T/nGgsD+z\n" +
                "exZid6RR8zjHkwMLvAl0x6wdKa46XJbFu+wTSu+vlowVY9rGzH+ttv4Fj6E2Y3DG\n" +
                "983/dVNJfXl00+Ts7rlvVcn9lI5dWvzsLoUOdhD4hsyKp53k8i4HexiD+0ugPeh9\n" +
                "4PKiyZOuMjSRNQSBUA3ElqJSRZz7nWvs/j6zhwHdFa+lN56575Mc5mrwr+KePwW5\n" +
                "DLt3izYpjwKffVuxUKPTrhvnOOg5kBBv0ihync21LSLds6jusxaMYUwUElO8KQyn\n" +
                "NUAeGPd/AgMBAAGjUzBRMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFORFOC3G\n" +
                "PjYKn7V1/BJHDmZ4W7J+MB8GA1UdIwQYMBaAFORFOC3GPjYKn7V1/BJHDmZ4W7J+\n" +
                "MA0GCSqGSIb3DQEBCwUAA4IBAQBqsP4ENtWTkNdsekYB+4hO/Afq20Ho0W8lyTkM\n" +
                "JO1UFDt/dzFAmTT4uM7pmwuQfqmCYjNDWon8nsdFno4tA0is3aiq6yIMAYzBE5ub\n" +
                "bnJMxldqLoWuakco1wYa3kZFzWPwecxgJ4ZlqTPGu0Loyjibt25IE9MfixyWDw+D\n" +
                "MhyfonLLgFb5jz7A3BTE63vlTp359uDbFb1nRdyoT31s3FUBK8jF4B5pWzPiLdct\n" +
                "bOMVjYUBs8aFC3fDXyGSr/RcjE4OOZQyTkYZn8zCPUJ4KqOPAUV9u9jx2FPvOcA3\n" +
                "1BjcmhYHqott+cnK1ITOjLe9EKejRZv/7/BFsmpzm2Zbq1KA\n" +
                "</ns0:publisher_bpki_ta>\n" +
                "</ns0:publisher_request>";


    @Test
    public void should_provision_non_hosted_publisher_repository() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
            .thenReturn(NON_HOSTED_CA_DATA);

        MvcResult result = mockMvc.perform(Rest.multipart(
            API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories",
            "file", PUBLISHER_REQUEST_XML.getBytes(StandardCharsets.UTF_8))).andReturn();

        final MockHttpServletResponse response = result.getResponse();

        verify(commandService).execute(isA(ProvisionNonHostedPublisherCommand.class));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getRedirectedUrl()).contains("non-hosted/publisher-repositories/");
    }

    @Test
    public void should_reject_non_existing_ca() throws Exception {

        MvcResult result = mockMvc.perform(Rest.multipart(
                API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories",
                "file", PUBLISHER_REQUEST_XML.getBytes(StandardCharsets.UTF_8))).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shoud_reject_bogus_publisher_request() throws Exception {
        CaName parsed = CaName.parse("ORG-1");
        when(certificateAuthorityViewService.findCertificateAuthorityByName(parsed.getPrincipal()))
                .thenReturn(NON_HOSTED_CA_DATA);

        MvcResult result = mockMvc.perform(Rest.multipart(
                API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories",
                "file", "BOGUS".getBytes(StandardCharsets.UTF_8))).andReturn();

        final MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).contains("error");
    }

    @Test
    public void should_reject_creating_too_many_nonhosted_publisher_repositories() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
            .thenReturn(NON_HOSTED_CA_DATA);
        when(commandService.execute(isA(ProvisionNonHostedPublisherCommand.class))).thenThrow(new CertificationResourceLimitExceededException("limit exceeded"));

        MvcResult result = mockMvc.perform(Rest.multipart(
            API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories",
            "file", PUBLISHER_REQUEST_XML.getBytes(StandardCharsets.UTF_8))).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("error", "limit exceeded");
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLISHER_REQUEST_XML, PUBLISHER_REQUEST_TAG_XML})
    public void should_list_non_hosted_publisher_repositories(String publisherRequestXml) throws Exception {
        var publisherRequest = new PublisherRequestSerializer().deserialize(publisherRequestXml);

        UUID publisherHandle = UUID.randomUUID();
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
                .thenReturn(NON_HOSTED_CA_DATA);
        when(certificateAuthorityViewService.findNonHostedPublisherRepositories(NON_HOSTED_CA_DATA.getName()))
             .thenReturn(Collections.singletonMap(publisherHandle, Pair.of(
                     publisherRequest,
                     new RepositoryResponse(
                     Optional.empty(),
                     URI.create("https://rpki.example.com/"),
                     publisherHandle.toString(),
                     URI.create("rsync://rpki.example.com/repo/handle/"),
                     Optional.empty(),
                     ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
                 )
             )));

        var resp = mockMvc.perform(Rest.get(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories").isMap())
                .andExpect(jsonPath("$.repositories[*].publisherHandle").value(publisherHandle.toString()));

        var tag = publisherRequest.getTag();

        if (tag.isPresent()) {
            resp.andExpect(jsonPath("$.repositories[*].tag").value(tag.get()));
        } else {
            resp.andExpect(jsonPath("$.repositories[*].tag").doesNotExist());
        }
    }

    @Test
    public void should_handle_non_existing_ca_when_listing_non_hosted_publisher_repositories() throws Exception {
        when(certificateAuthorityViewService.findNonHostedPublisherRepositories(NON_HOSTED_CA_DATA.getName()))
                .thenThrow(new EntityNotFoundException("non hosted missing"));

        MvcResult result = mockMvc.perform(Rest.get(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories")).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getContentAsString()).contains("error");
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLISHER_REQUEST_XML, PUBLISHER_REQUEST_TAG_XML})
    public void should_download_non_hosted_publisher_repository_response(String publisherRequestXml) throws Exception {
        var publisherRequest = new PublisherRequestSerializer().deserialize(publisherRequestXml);

        UUID publisherHandle = UUID.randomUUID();
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
                .thenReturn(NON_HOSTED_CA_DATA);
        when(certificateAuthorityViewService.findNonHostedPublisherRepositories(NON_HOSTED_CA_DATA.getName()))
            .thenReturn(Collections.singletonMap(publisherHandle, Pair.of(
                    publisherRequest,
                    new RepositoryResponse(
                        Optional.empty(),
                        URI.create("https://rpki.example.com/"),
                        publisherHandle.toString(),
                        URI.create("rsync://rpki.example.com/repo/handle/"),
                        Optional.empty(),
                        ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
                    )
            )));

        MvcResult result = mockMvc.perform(Rest.get(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories/" + publisherHandle + "/repository-response")).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_XML);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());

        // Parse the response and validate that the tag matches
        var publisherResponse = new RepositoryResponseSerializer().deserialize(response.getContentAsString());
        assertThat(publisherResponse.getTag()).isEqualTo(publisherRequest.getTag());
        assertThat(publisherResponse.getPublisherHandle()).isEqualTo(publisherHandle.toString());
    }

    @Test
    public void should_handle_non_existing_ca_when_downloading_non_hosted_publisher_repository_response() throws Exception {
        UUID publisherHandle = UUID.randomUUID();
        when(certificateAuthorityViewService.findNonHostedPublisherRepositories(NON_HOSTED_CA_DATA.getName()))
                .thenThrow(new EntityNotFoundException("non hosted missing"));

        MvcResult result = mockMvc.perform(Rest.get(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories/" + publisherHandle + "/repository-response")).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getContentAsString()).contains("error");
    }

    @Test
    public void should_not_delete_publisher_from_bogus_ca() throws Exception {
        String rawCaName = "bogus-ca";
        // Raw CA verification will be done by AbstractCaRestService
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any()))
                .thenThrow(new CaNameInvalidException(rawCaName));

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/rawCaName/non-hosted/publisher-repositories/"+UUID.randomUUID()))
                .andExpect(status().is(400));

        verifyNoInteractions(commandService);
    }

    @Test
    public void should_delete_non_hosted_publisher_repository() throws Exception {
        UUID publisherHandle = UUID.randomUUID();
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
                .thenReturn(NON_HOSTED_CA_DATA);

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories/" + publisherHandle))
                .andExpect(status().is(204));

        verify(commandService).execute(isA(DeleteNonHostedPublisherCommand.class));
    }

    @Test
    public void should_reject_delete_nonexistent_non_hosted_publisher_repository() throws Exception {
        UUID publisherHandle = UUID.randomUUID();
        when(certificateAuthorityViewService.findCertificateAuthorityByName(NON_HOSTED_CA_DATA.getName()))
                .thenReturn(null);

        MvcResult result = mockMvc.perform(Rest.delete(API_URL_PREFIX + "/ORG-1/non-hosted/publisher-repositories/" + publisherHandle)).andReturn();

        final MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
