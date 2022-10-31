package net.ripe.rpki.ripencc.services.impl;

import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceServicesClientTest {
    @Test
    void fetchAllResources() {
        ClassPathResource file = new ClassPathResource("/ca-resources/total-resources.json");
        ResourceServicesClient subject = new StaticResourceServicesClient(file);
        ResourceServicesClient.TotalResources resources = subject.fetchAllResources();
        assertThat(resources).isNotNull();
        assertThat(resources.getAllMembersResources()).isNotNull();
        assertThat(resources.getRipeNccDelegations()).isNotNull();
    }
}