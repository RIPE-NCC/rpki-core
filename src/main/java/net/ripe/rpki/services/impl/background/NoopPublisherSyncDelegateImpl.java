package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnMissingBean(KrillNonHostedPublisherRepositoryBean.class)
public class NoopPublisherSyncDelegateImpl implements PublisherSyncDelegate {
    @Override
    public void runService() {
        log.info("Publisher sync is disabled, noop.");
    }
}
