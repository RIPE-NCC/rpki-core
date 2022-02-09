package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.security.RunAsUserHolder.Get;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.asAdmin;

@Component
public class CertificationDBHealthCheck extends Health.Check {

    private final CertificateAuthorityViewService caViewService;
    private final RepositoryConfiguration configuration;

    @Autowired
    public CertificationDBHealthCheck(RepositoryConfiguration configuration, CertificateAuthorityViewService caViewService) {
        super("Certification Database");
        this.configuration = configuration;
        this.caViewService = caViewService;
    }

    @Override
    public Health.Status check() {
        return asAdmin((Get<Health.Status>) () -> {
            try {
                caViewService.findCertificateAuthorityByName(configuration.getProductionCaPrincipal());
                return Health.ok();
            } catch (Exception e) {
                return Health.error(e.getMessage());
            }
        });
    }
}
