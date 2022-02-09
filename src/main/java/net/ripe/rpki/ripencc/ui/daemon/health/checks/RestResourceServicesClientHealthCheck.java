package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.ports.ResourceServicesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RestResourceServicesClientHealthCheck extends Health.Check {

    private final ResourceServicesClient resourceServicesClient;

    @Autowired
    public RestResourceServicesClientHealthCheck(ResourceServicesClient resourceServicesClient) {
        super("rest-resource-services");
        this.resourceServicesClient = resourceServicesClient;
    }

    @Override
    public Health.Status check() {
        if (resourceServicesClient.isAvailable()) {
            return Health.ok();
        }
        return Health.warning("not available");
    }
}
