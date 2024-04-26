package net.ripe.rpki.ripencc.ui.daemon.health;

import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.util.List;

@Component
public class HealthChecks {

    private final List<Health.Check> checks;

    @Inject
    public HealthChecks(List<Health.Check> checks) {
        this.checks = checks;
    }

    public List<Health.Check> getChecks() {
        return checks;
    }
}
