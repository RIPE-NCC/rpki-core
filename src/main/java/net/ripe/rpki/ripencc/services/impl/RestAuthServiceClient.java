package net.ripe.rpki.ripencc.services.impl;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;

@Component
public class RestAuthServiceClient implements AuthServiceClient {
    private final Counter authServiceLoadSuccess;
    private final Counter authServiceLoadFailed;
    private final AtomicBoolean authServiceAvailable = new AtomicBoolean(true);

    private final String apiKey;
    private final Client webResource;
    private final String accountServiceUrl;
    private final Gson gson = new Gson();


    @Inject
    public RestAuthServiceClient(
            @Value("${auth.service.url:http://localhost}") String accountServiceUrl,
            @Value("${auth.service.read.timeout.milliseconds}") int readTimeout,
            @Value("${auth.service.connect.timeout.milliseconds}") int connectTimeout,
            @Value("${auth.service.api.key:unknown}") String apiKey,
            MeterRegistry meterRegistry) {
        this.accountServiceUrl = Preconditions.checkNotNull(accountServiceUrl, "URL for account-service is required");
        this.apiKey = Preconditions.checkNotNull(apiKey, "API key is required");
        Preconditions.checkArgument(readTimeout > 0, "Read timeout is required");
        Preconditions.checkArgument(connectTimeout > 0, "Connection timeout is required");
        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        clientConfig.property(ClientProperties.READ_TIMEOUT, 31 * 1000);
        webResource = ClientBuilder.newClient(clientConfig);

        authServiceLoadSuccess = Counter.builder("rpkicore.auth.service.client.call")
                .tag("operation", "getUserEmail")
                .tag("status", "success")
                .description("Number of resolved names for UUIDs")
                .baseUnit("total")
                .register(meterRegistry);
        authServiceLoadFailed = Counter.builder("rpkicore.auth.service.client.call")
                .tag("operation", "getUserEmail")
                .tag("status", "failure")
                .description("Number of resolved names for UUIDs")
                .baseUnit("total")
                .register(meterRegistry);
        Gauge.builder("rpkicore.auth.service.client.available", () -> authServiceAvailable.get() ? 1 : 0)
                .description("Is the auth service available")
                .baseUnit("info")
                .register(meterRegistry);
    }

    @Override
    public boolean isAvailable() {
        try {
            final boolean available = SUCCESSFUL == executeGET("groups").getStatusInfo().getFamily();
            authServiceAvailable.set(available);
            return available;
        } catch (Exception e) {
            authServiceAvailable.set(false);
            return false;
        }
    }

    @Override
    public Optional<String> getUserEmail(UUID userUuid) {
        final Response httpResponse = executeGET("accounts/" + userUuid.toString());
        final Response.StatusType status = httpResponse.getStatusInfo();

        switch (status.getFamily()) {
            case SUCCESSFUL:
                try {
                    final AccountServiceResponse response = gson.fromJson(httpResponse.readEntity(String.class), AccountServiceResponse.class);
                    final Optional<String> result = Optional.ofNullable(response.response.content.email);
                    authServiceLoadSuccess.increment();
                    return result;
                } catch (Exception e) {
                    authServiceLoadFailed.increment();
                    throw e;
                }
            case CLIENT_ERROR:
                authServiceLoadFailed.increment();
                return Optional.empty();
            default:
                authServiceLoadFailed.increment();
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

