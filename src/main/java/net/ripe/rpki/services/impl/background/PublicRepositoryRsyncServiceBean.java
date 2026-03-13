package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.publication.api.PublicationWriteService;
import net.ripe.rpki.services.impl.handlers.PublicationMetrics;
import org.springframework.stereotype.Service;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RSYNC_SERVICE;

/**
 * Writes all published objects that should be publicly available to the local rsync repository.
 */
@Service(PUBLIC_REPOSITORY_RSYNC_SERVICE)
public class PublicRepositoryRsyncServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final PublicationWriteService publicationWriteService;
    private final PublishedObjectsServiceBean publishedObjectsService;
    private final PublicationMetrics publicationMetrics;

    public PublicRepositoryRsyncServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                            PublicationWriteService publicationWriteService,
                                            PublishedObjectsServiceBean publishedObjectsService,
                                            PublicationMetrics publicationMetrics) {
        super(backgroundTaskRunner);
        this.publicationWriteService = publicationWriteService;
        this.publishedObjectsService = publishedObjectsService;
        this.publicationMetrics = publicationMetrics;
    }

    @Override
    public String getName() {
        return "Public Repository Rsync Service";
    }

    @Override
    @SneakyThrows
    protected void runService(Map<String, String> parameters) {
        var pos = publishedObjectsService.getPublishedObjects();
        if (pos.isBelowThreshold()) {
            publicationMetrics.getRsyncPublicationUnderThreshold().increment();
            log.error("Will not publish objects to the rsync repository: the number of objects {} is smaller than the minimal threshold {}.",
                    pos.objects().size(), publishedObjectsService.getMinimalObjectCount());
        } else {
            publicationWriteService.writeAll(pos.objects());
        }
    }
}
