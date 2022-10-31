package net.ripe.rpki.ripencc.services.impl;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(name="resource.services.source", havingValue = "static")
public class StaticResourceServicesClient implements ResourceServicesClient {

    private final org.springframework.core.io.Resource file;

    public StaticResourceServicesClient(
            @Value("${resource.services.static.file:classpath:/ca-resources/total-resources.json}") org.springframework.core.io.Resource file
    ) {
        this.file = file;
        log.info("Using static resource service client with source: {}.", file.toString());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @SneakyThrows
    @Override
    public TotalResources fetchAllResources() {
        try (InputStream in = file.getInputStream()) {
            String data = IOUtils.toString(in, StandardCharsets.UTF_8);
            TotalResourceResponse response = new Gson().fromJson(data, TotalResourceResponse.class);
            return response.getResponse().getContent();
        }
    }
}
