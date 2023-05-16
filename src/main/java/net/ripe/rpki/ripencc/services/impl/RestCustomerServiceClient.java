package net.ripe.rpki.ripencc.services.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class RestCustomerServiceClient implements CustomerServiceClient {

    private final Gson gson = new Gson();

    private final Client customerService;
    private final String customerServiceUrl;
    private final String apiKey;

    @Inject
    public RestCustomerServiceClient(
            @Value("${customer.service.url}") String customerServiceUrl,
            @Value("${customer.service.api.key}") String apiKey,
            @Value("${customer.service.read.timeout.milliseconds}") int readTimeout,
            @Value("${customer.service.connect.timeout.milliseconds}") int connectTimeout) {
        this.apiKey = apiKey;
        this.customerServiceUrl = customerServiceUrl;
        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
        customerService = ClientBuilder.newClient(clientConfig);
    }

    @Override
    public boolean isAvailable() {
        try (Response response = customerServiceTarget().path("monitoring/healthcheck").request().head()) {
            return response.getStatus() == 200;
        }
    }

    @Override
    public List<MemberSummary> findAllMemberSummaries() {
        Invocation.Builder requestWithHeader = customerServiceTarget().path("api/members/basicinfo")
                .request().header("ncc-internal-api-key", apiKey);

        MemberSummary[] memberSummaries = httpGetJson(requestWithHeader, MemberSummary[].class);
        return memberSummaries == null ? Collections.emptyList() : Arrays.asList(memberSummaries);
    }

    private <T> T httpGetJson(Invocation.Builder builder, Class<T> responseType) {
        try (Response clientResponse = builder.get()) {
            switch (clientResponse.getStatus()) {
                case 200:
                    return gson.fromJson(clientResponse.readEntity(String.class), responseType);
                case 404:
                    return null;
                default:
                    throw new IllegalArgumentException(builder + " GET failure: " + clientResponse.getStatusInfo());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(builder + " GET failure: " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    WebTarget customerServiceTarget() {
        return customerService.target(customerServiceUrl);
    }

}
