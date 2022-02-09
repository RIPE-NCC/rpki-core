package net.ripe.rpki.ripencc.services.impl;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;

@Component
public class RestAuthServiceClient implements AuthServiceClient {

    private final String apiKey;
    private final Client webResource;
    private final String accountServiceUrl;
    private final Gson gson = new Gson();


    @Inject
    public RestAuthServiceClient(
            @Value("${auth.service.url:http://localhost}") String accountServiceUrl,
            @Value("${auth.service.read.timeout.milliseconds}") int readTimeout,
            @Value("${auth.service.connect.timeout.milliseconds}") int connectTimeout,
            @Value("${auth.service.api.key:unknown}") String apiKey) {
        this.accountServiceUrl = Preconditions.checkNotNull(accountServiceUrl, "URL for account-service is required");
        this.apiKey = Preconditions.checkNotNull(apiKey, "API key is required");
        Preconditions.checkArgument(readTimeout > 0, "Read timeout is required");
        Preconditions.checkArgument(connectTimeout > 0, "Connection timeout is required");
        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        clientConfig.property(ClientProperties.READ_TIMEOUT, 31 * 1000);
        webResource = ClientBuilder.newClient(clientConfig);
    }

    @Override
    public boolean isAvailable() {
        try {
            return SUCCESSFUL == executeGET("groups").getStatusInfo().getFamily();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<String> getUserEmail(UUID userUuid) {
        final Response httpResponse = executeGET("accounts/" + userUuid.toString());
        final Response.StatusType status = httpResponse.getStatusInfo();

        switch (status.getFamily()) {
            case SUCCESSFUL:
                final AccountServiceResponse response = gson.fromJson(httpResponse.readEntity(String.class), AccountServiceResponse.class);
                return Optional.ofNullable(response.response.content.email);
            case CLIENT_ERROR:
                return Optional.empty();
            default:
                throw new IllegalArgumentException(httpResponse.getLocation() + " GET failure: " + httpResponse.getStatusInfo());
        }
    }

    private Response executeGET(String path) {
        return webResource
                .target(accountServiceUrl)
                .path(path)
                .request(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, apiKey)
                .get();
    }

    private class RipeAccessUser {
        String name;
        String email;
        String uuid;
        Boolean isActive;
    }

    private class AccountServiceContent {
        RipeAccessUser content;
    }

    private class AccountServiceResponse {
        AccountServiceContent response;
    }
}

