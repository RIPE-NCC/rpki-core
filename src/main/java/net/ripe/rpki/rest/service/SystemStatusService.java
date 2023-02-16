package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = "/api/public/system-status", produces = MediaType.APPLICATION_JSON)
@Tag(name = "/api/public/system-status", description = "System status")
public class SystemStatusService extends RestService {

    private final RepositoryConfiguration repositoryConfiguration;
    private final ActiveNodeService activeNodeService;
    private final BackgroundServices backgroundServices;

    @Autowired
    public SystemStatusService(RepositoryConfiguration repositoryConfiguration,
                               ActiveNodeService activeNodeService,
                               BackgroundServices backgroundServices) {
        this.repositoryConfiguration = repositoryConfiguration;
        this.activeNodeService = activeNodeService;
        this.backgroundServices = backgroundServices;
    }

    @GetMapping
    @Operation(summary = "Return generic system status")
    public ResponseEntity<SystemStatus> status() {
        log.debug("Returning generic system status");
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(getSystemStatus());
    }

    private SystemStatus getSystemStatus() {

        final Configuration configuration = new Configuration(
            repositoryConfiguration.getLocalRepositoryDirectory().getAbsolutePath(),
            repositoryConfiguration.getPublicRepositoryUri().toASCIIString(),
            activeNodeService.getCurrentNodeName(),
            activeNodeService.getActiveNodeName());

        final Map<String, String> servicesStatuses = new HashMap<>();
        backgroundServices.getAllServices().
            forEach((name, service) -> servicesStatuses.put(name, status(service)));

        return new SystemStatus(configuration, servicesStatuses);
    }

    private String status(BackgroundService service) {
        return service.isActive() ? service.getStatus() : "inactive";
    }

    @Value
    public static class Configuration {
        String localRepositoryDirectory;
        String publicRepositoryURI;
        String instanceName;
        String activeNode;
    }

    @Value
    public static class SystemStatus {
        private Configuration configuration;
        private Map<String, String> services;
    }

}
