package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLISHER_SYNC_SERVICE;

@Slf4j
@Service(PUBLISHER_SYNC_SERVICE)
public class PublisherSyncService extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private final PublisherSyncDelegate publisherSyncDelegate;

    @Inject
    public PublisherSyncService(ActiveNodeService activeNodeService,
                                PublisherSyncDelegate publisherSyncDelegate) {
        super(activeNodeService);
        this.publisherSyncDelegate = publisherSyncDelegate;
    }
    @Override
    protected void runService() {
            publisherSyncDelegate.runService();
    }
    @Override
    public String getName() {
        return "Publisher repositories sync service";
    }
}
