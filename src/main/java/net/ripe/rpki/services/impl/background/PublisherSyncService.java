package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLISHER_SYNC_SERVICE;

@Slf4j
@Service(PUBLISHER_SYNC_SERVICE)
public class PublisherSyncService extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private final PublisherSyncDelegate publisherSyncDelegate;

    @Inject
    public PublisherSyncService(BackgroundTaskRunner backgroundTaskRunner,
                                PublisherSyncDelegate publisherSyncDelegate) {
        super(backgroundTaskRunner);
        this.publisherSyncDelegate = publisherSyncDelegate;
    }
    @Override
    protected void runService(Map<String, String> parameters) {
            publisherSyncDelegate.runService();
    }
    @Override
    public String getName() {
        return "Publisher repositories sync service";
    }
}
