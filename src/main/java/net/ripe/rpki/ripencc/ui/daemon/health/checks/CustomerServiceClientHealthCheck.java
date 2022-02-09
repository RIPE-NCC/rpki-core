package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.services.impl.CustomerServiceClient;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import org.springframework.beans.factory.annotation.Autowired;

public class CustomerServiceClientHealthCheck extends Health.Check {

    private final CustomerServiceClient customerService;

    @Autowired
    public CustomerServiceClientHealthCheck(CustomerServiceClient customerService) {
        super("customer-service-client");
        this.customerService = customerService;
    }

    @Override
    public Health.Status check() {
        if (customerService.isAvailable()) {
            return Health.ok();
        } else {
            return Health.warning("not available");
        }
    }
}
