package net.ripe.rpki.ripencc.services.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean.MONITORING_TARGET;
import static net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean.PUBD_PUBLISHERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


public class KrillNonHostedPublisherRepositoryBeanTest {

    private static final int PORT = 6666;
    private static final String BASE_URL = "http://localhost:" + PORT + "/";
    private static final String API_TOKEN = "apiToken";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    KrillNonHostedPublisherRepositoryBean subject;

    @Before
    public void setUp() throws Exception {
        subject = new KrillNonHostedPublisherRepositoryBean(BASE_URL, API_TOKEN, getInsecureContext());
    }

    private void stubMonitoringTargets() {
        stubFor(get(urlEqualTo(MONITORING_TARGET))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody("{\"version\":\"0.10.3\",\"started\":1645619021}")
                        .withStatus(200)));
    }

    private void stubForGetPublishers() {
        stubFor(get(urlEqualTo(PUBD_PUBLISHERS))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer "+API_TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(readFromFile("/repository-publisher/publishers.json"))));
    }



    @Test
    public void shouldRegisterPublisherRequest() {
        stubFor(post(urlEqualTo(PUBD_PUBLISHERS))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer "+API_TOKEN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(readFromFile("/repository-publisher/repository_response.json"))));

        stubFor(get(urlEqualTo(PUBD_PUBLISHERS))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer "+API_TOKEN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(readFromFile("/repository-publisher/publishers.json"))));

        String requestXML = readFromFile("/repository-publisher/publisher_request.xml");
        PublisherRequest publisherRequest = new PublisherRequestSerializer().deserialize(requestXML);

        UUID publisherHandle = UUID.randomUUID();

        RepositoryResponse repositoryResponse = subject.provisionPublisher(publisherHandle, publisherRequest);
        // publishers and repositories have a different BPKI - unless objects are from the same krill instance.
        assertThat(repositoryResponse.getRepositoryBpkiTa().getPublicKey()).isNotEqualTo(publisherRequest.getPublisherBpkiTa().getPublicKey());

        // Handle copied
        assertThat(repositoryResponse.getPublisherHandle()).isEqualTo(publisherRequest.getPublisherHandle());
        assertThat(repositoryResponse.getTag()).isPresent();

        // And ensure the critical attributes have sensible values
        assertThat(repositoryResponse.getServiceUri()).hasScheme("https");
        assertThat(repositoryResponse.getRrdpNotificationUri()).hasValueSatisfying(uri -> assertThat(uri).hasScheme("https"));
        assertThat(repositoryResponse.getSiaBase()).hasScheme("rsync");

        // krill specific - likely not required by RFC
        assertThat(repositoryResponse.getServiceUri()).hasScheme("https");
    }


    @Test
    public void shouldBeAvailable() {
        stubMonitoringTargets();
        assertTrue(subject.isAvailable());
    }

    @Test
    public void shouldBeInitialized() {
        stubForGetPublishers();
        assertTrue(subject.isInitialized());
    }

    @Test
    public void shouldDeleteRepositoryPublishers() {
        UUID publisherToRemove = UUID.randomUUID();
        stubForDeletePublishers(publisherToRemove);
        Response response = subject.deletePublisher(publisherToRemove);
        assertEquals(200, response.getStatus());
    }

    private void stubForDeletePublishers(UUID publisherToRemove) {
        stubFor(delete(urlEqualTo(PUBD_PUBLISHERS + "/" + publisherToRemove)).withHeader(HttpHeaders.AUTHORIZATION,
                equalTo("Bearer " + API_TOKEN)).willReturn(aResponse().withStatus(200)));
    }

    @Test
    public void shouldListAvailablePublishers() {

        stubForGetPublishers();
        Set<UUID> uuids = subject.listPublishers();
        String publisher = JsonTestUtils.readJsonFile("/repository-publisher/publishers.json")
                .getAsJsonObject().getAsJsonArray("publishers").get(0)
                .getAsJsonObject().get("handle").getAsString();
        
        assertTrue(uuids.contains(UUID.fromString(publisher)));
    }

    @Test
    public void shouldListAvailablePublishersWhenNotIitialized() {

        stubForGetPublishers();

        Set<UUID> uuids = subject.listPublishers();

        String publisher = JsonTestUtils.readJsonFile("/repository-publisher/publishers.json")
                .getAsJsonObject().getAsJsonArray("publishers").get(0)
                .getAsJsonObject().get("handle").getAsString();

        assertTrue(uuids.contains(UUID.fromString(publisher)));
    }
    @SneakyThrows
    private String readFromFile(String resourcePath) {
        final Resource resource = new ClassPathResource(resourcePath);
        return Resources.toString(resource.getURL(), StandardCharsets.UTF_8);
    }

    public static SSLContext getInsecureContext() throws GeneralSecurityException {
        TrustManager[] dummyTrustManager = new TrustManager[]{mock(X509ExtendedTrustManager.class)};
        SSLContext insecureContext = SSLContext.getInstance("TLS");
        insecureContext.init(null, dummyTrustManager, new SecureRandom());
        return insecureContext;
    }
}
