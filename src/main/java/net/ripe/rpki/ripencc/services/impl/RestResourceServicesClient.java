package net.ripe.rpki.ripencc.services.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.apache.commons.lang.ArrayUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriTemplate;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name="resource.services.url")
@Primary
class RestResourceServicesClient implements ResourceServicesClient {

    private static final String PAGE_SIZE = "5000";
    private static final String MEMBER_RESOURCES = "member-resources";
    private static final String MONITORING_HEALTHCHECK = "monitoring/healthcheck";

    private final Gson gson = new Gson();
    private final Client resourceServices;
    private final Boolean enableAsns;
    private final String resourceServicesUrl;
    private final String apiKey;

    public RestResourceServicesClient(
            @Value("${resource.services.url}") String resourceServicesUrl,
            @Value("${resource.services.enable.asn}") Boolean enableAsns,
            @Value("${resource.services.apiKey}") String apiKey) {
        Preconditions.checkState(enableAsns, "ASNs should be enabled in almost all circumstances. Disabling would break bgpsec certificates.");
        this.resourceServicesUrl = resourceServicesUrl;
        this.enableAsns = enableAsns;
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
        try {
            return resourceServices.target(resourceServicesUrl).path(MONITORING_HEALTHCHECK)
                    .request(MediaType.APPLICATION_JSON)
                    .head().getStatus() == 200;
        } catch (Exception t) {
            return false;
        }
    }

    @Override
    public IpResourceSet findProductionCaDelegations() {
        final IpResourceSet result = new IpResourceSet();
        Stream.of(
                findDelegationsByType("ipv4-delegations").stream(),
                findDelegationsByType("ipv6-delegations").stream(),
                findDelegationsByType("asn-delegations").stream())
                .reduce(Stream::concat)
                .orElseGet(Stream::empty)
                .map(delegation -> IpResource.parse(delegation.range))
                .forEach(result::add);
        return result;
    }

    private List<Delegation> findDelegationsByType(String delegationsIndexLink) {
        List<Delegation> result = new ArrayList<Delegation>();

        Link link = getIndexLink(delegationsIndexLink);
        while (link != null) {
            DelegationsResponse response = httpGetJson(
                    linkTarget(link.href).queryParam("page-size", PAGE_SIZE),
                    DelegationsResponse.class);
            if (response == null || response.response == null || response.response.results == null) {
                throw new RuntimeException("Invalid response: " + response);
            }
            result.addAll(response.response.results);
            link = findLinkByRelType(response.response.links, "next");
        }

        return result;
    }

    @Override
    public MemberResources fetchAllMemberResources() {
        final Link link = getIndexLink(MEMBER_RESOURCES);

        final MemberResourceResponse response = httpGetJson(linkTarget(link.href), MemberResourceResponse.class);
        if (response == null || response.getResponse() == null || response.getResponse().getContent() == null) {
            throw new RuntimeException("invalid response: " + response);
        }
        final MemberResources memberResources = response.getResponse().getContent();
        if (!enableAsns) {
            memberResources.ignoreAsns();
        }
        return memberResources;
    }

    @VisibleForTesting
    Client getHttpClient() {
        return resourceServices;
    }

    @VisibleForTesting
    <T> T httpGetJson(WebTarget webResource, Class<T> responseType) {
        log.info("HTTP GET " + webResource.getUri());
        final Response clientResponse = webResource.request(MediaType.APPLICATION_JSON_TYPE)
                .header("X-API_KEY", apiKey)
                .get();
        if (clientResponse.getStatus() != 200) {
            throw new IllegalArgumentException(webResource.getUri() + " GET failure: " + clientResponse.getStatusInfo());
        }
        return gson.fromJson(clientResponse.readEntity(String.class), responseType);
    }

    private static Link getLinkByRelType(List<Link> links, String relType) {
        Link result = findLinkByRelType(links, relType);
        if (result == null) {
            throw new IllegalArgumentException("no link of type '" + relType + "' found in: " + links);
        }
        return result;
    }

    private static Link findLinkByRelType(List<Link> links, String relType) {
        if (links == null) {
            return null;
        }
        return links.stream()
                .filter(link -> ArrayUtils.contains(link.rel.split("\\s+"), relType))
                .findFirst()
                .orElse(null);
    }

    private Link getIndexLink(String relType) {
        IndexResponse index = httpGetJson(resourcesTarget(), IndexResponse.class);
        return getLinkByRelType(index.response.links, relType);
    }

    private WebTarget linkTarget(String path) {
        return resourceServices.target(LinkUtil.linkTarget(URI.create(resourceServicesUrl), path));
    }

    private WebTarget resourcesTarget() {
        return resourceServices.target(resourceServicesUrl);
    }

    private static class IndexResponse {
        private IndexContent response;
    }

    private static class IndexContent {
        private List<Link> links;
        private List<Link> linkTemplates;
    }

    private static class Link {
        private String rel;
        private String href;

        public String render(Map<String, Object> parameters) {
            return new UriTemplate(href).expand(parameters).toString();
        }
    }

    private static class DelegationsResults {
        private List<Link> links;
        private List<Delegation> results;
    }

    private static class DelegationsResponse {
        private DelegationsResults response;
    }
}
