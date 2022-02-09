package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.services.impl.AuthServiceClient;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthServiceClientHealthCheck extends Health.Check {

    private final AuthServiceClient authServiceClient;

    @Autowired
    public AuthServiceClientHealthCheck(AuthServiceClient authServiceClient) {
        super("auth-service-client");
        this.authServiceClient = authServiceClient;
    }

    @Override
    public Health.Status check() {
        if (authServiceClient.isAvailable()) {
            return Health.ok();
        } else {
            return Health.warning("not available");
        }
    }
}

