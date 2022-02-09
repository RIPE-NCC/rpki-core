package net.ripe.rpki.ripencc.services.impl;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(name="resource.services.url", matchIfMissing = true, havingValue = "stub_url_that_never_happens")
public class StubResourceServicesClient implements ResourceServicesClient {

    public StubResourceServicesClient() {
        log.info("Will use stub resource service client.");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @SneakyThrows
    @Override
    public IpResourceSet findProductionCaDelegations() {
        final String productionCaResources = readAsString("/ca-resources/production-delegations.txt");
        return IpResourceSet.parse(productionCaResources);
    }

    @SneakyThrows
    @Override
    public MemberResources fetchAllMemberResources() {
        final String productionCaResources = readAsString("/ca-resources/member-resources.json");
        final MemberResourceResponse response = new Gson().fromJson(productionCaResources, MemberResourceResponse.class);
        return response.getResponse().getContent();
    }

    private String readAsString(String s) throws IOException {
        final InputStream resourceAsStream = this.getClass().getResourceAsStream(s);
        return IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
    }

}
