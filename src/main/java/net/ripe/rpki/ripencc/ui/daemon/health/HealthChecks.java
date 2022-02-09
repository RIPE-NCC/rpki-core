package net.ripe.rpki.ripencc.ui.daemon.health;

import net.ripe.rpki.ripencc.ui.daemon.health.checks.AuthServiceClientHealthCheck;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.CertificateRepositoryUpToDateHealthCheck;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.CertificationDBHealthCheck;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.ResourceCacheUpToDateHealthCheck;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.RestResourceServicesClientHealthCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HealthChecks {

    private List<Health.Check> checks = new ArrayList<>();

    @Autowired
    public HealthChecks(
            AuthServiceClientHealthCheck authServiceClientHealthCheck,
            CertificateRepositoryUpToDateHealthCheck certificateRepositoryUpToDateHealthCheck,
            CertificationDBHealthCheck certificationDBHealthCheck,
            ResourceCacheUpToDateHealthCheck resourceCacheUpToDateHealthCheck,
            RestResourceServicesClientHealthCheck restResourceServicesClientHealthCheck) {
        checks.add(authServiceClientHealthCheck);
        checks.add(certificateRepositoryUpToDateHealthCheck);
        checks.add(certificationDBHealthCheck);
        checks.add(resourceCacheUpToDateHealthCheck);
        checks.add(restResourceServicesClientHealthCheck);
    }

    public List<Health.Check> getChecks() {
        return checks;
    }
}
