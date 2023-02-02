package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CryptoHealthCheck extends Health.Check {

    private final CryptoChecker cryptoChecker;

    @Autowired
    public CryptoHealthCheck(CryptoChecker cryptoChecker) {
        super("crypto");
        this.cryptoChecker = cryptoChecker;
    }

    @Override
    public Health.Status check() {
        // We delegate the check to an extra component to avoid having @Transactional on this method
        // which breaks injection of CryptoHealthCheck to the HealthChecks bean.
        return cryptoChecker.getHealthStatus();
    }
}
