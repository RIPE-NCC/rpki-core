package net.ripe.rpki.ripencc.services.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.security.ApiKeySecurity;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Slf4j
@Component
@ConditionalOnProperty(name="resource.services.source", havingValue = "rsng", matchIfMissing = true)
class RestResourceServicesClient implements ResourceServicesClient {

    private static final String TOTAL_RESOURCES = "total-resources";
    static final String HEALTHCHECK_PATH = "charged-resources-api/actuator/health";

    private final Gson gson = new Gson();
    private final Client resourceServices;
    private final String resourceServicesUrl;
    private final String apiKey;

    public RestResourceServicesClient(
            @Value("${resource.services.url}") String resourceServicesUrl,
            @Value("${resource.services.apiKey}") String apiKey) {
        this.resourceServicesUrl = resourceServicesUrl;
        this.apiKey = apiKey;

        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        clientConfig.property(ClientProperties.READ_TIMEOUT, 31 * 1000);
        resourceServices = ClientBuilder.newClient(clientConfig);
        log.info("Will use resource service client pointing to {}.", resourceServicesUrl);
    }

    @Override
    public boolean isAvailable() {
        log.debug("Checking if internet resources REST API is available");
        try (
            Response response = resourceServices.target(resourceServicesUrl).path(HEALTHCHECK_PATH)
                .request(MediaType.APPLICATION_JSON)
                .head()
        ) {
            return response.getStatus() == 200;
        } catch (Exception t) {
            return false;
        }
    }

    @Override
    public TotalResources fetchAllResources() {
        final TotalResourceResponse response = httpGetJson(resourcesTarget().path(TOTAL_RESOURCES), TotalResourceResponse.class);
        if (response == null || response.getResponse() == null || response.getResponse().getContent() == null) {
            throw new RuntimeException("Invalid response: " + response);
        }
        return response.getResponse().getContent();
    }

    MemberResourceResponse fetchMemberResources(long membershipId) {
        return httpGetJson(
            resourcesTarget().path("member-resources/" + membershipId),
            MemberResourceResponse.class);
    }

    private <T> T httpGetJson(WebTarget webResource, Class<T> responseType) {
        log.info("HTTP GET " + webResource.getUri());
        try (
            Response clientResponse = webResource.request(MediaType.APPLICATION_JSON_TYPE)
                .header(ApiKeySecurity.API_KEY_HEADER, apiKey)
                .get()
        ) {
            if (clientResponse.getStatus() != 200) {
                throw new IllegalArgumentException(webResource.getUri() + " GET failure: " + clientResponse.getStatusInfo());
            }
            return gson.fromJson(clientResponse.readEntity(String.class), responseType);
        }
    }

    @VisibleForTesting
    WebTarget resourcesTarget() {
        return resourceServices.target(resourceServicesUrl);
    }
}
