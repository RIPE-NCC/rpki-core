package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
@ConditionalOnBean(KrillNonHostedPublisherRepositoryBean.class)
public class KrillNonHostedPublisherRepositoryHealthCheck extends Health.Check {

    private final KrillNonHostedPublisherRepositoryBean krillClient;

    @Inject
    public KrillNonHostedPublisherRepositoryHealthCheck(KrillNonHostedPublisherRepositoryBean krillClient) {
        super("krill-non-hosted-publisher-repository");
        this.krillClient = krillClient;
    }

    @Override
    public Health.Status check() {
        if (krillClient.isAvailable()) {
            return Health.ok();
        } else {
            return Health.warning("not available");
        }
    }
}
