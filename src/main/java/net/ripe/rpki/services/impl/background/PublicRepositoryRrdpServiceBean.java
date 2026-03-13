package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.services.impl.handlers.PublicationMetrics;
import net.ripe.rpki.services.impl.handlers.PublicationSupport;
import org.springframework.stereotype.Service;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RRDP_SERVICE;

/**
 * Publishes all RPKI objects that should be publicly available to the (RRDP) publication server.
 */
@Service(PUBLIC_REPOSITORY_RRDP_SERVICE)
public class PublicRepositoryRrdpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final PublicationSupport publicationSupport;
    private final PublishedObjectsServiceBean publishedObjectsService;
    private final PublicationMetrics publicationMetrics;

    public PublicRepositoryRrdpServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                           PublicationSupport publicationSupport,
                                           PublishedObjectsServiceBean publishedObjectsService,
                                           PublicationMetrics publicationMetrics) {
        super(backgroundTaskRunner);
        this.publicationSupport = publicationSupport;
        this.publishedObjectsService = publishedObjectsService;
        this.publicationMetrics = publicationMetrics;
    }

    @Override
    public String getName() {
        return "Public Repository RRDP Service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        var pos = publishedObjectsService.getPublishedObjects();
        if (pos.isBelowThreshold()) {
            publicationMetrics.getRrdpPublicationUnderThreshold().increment();
            log.error("Will not publish objects to the RRDP repository: the number of objects {} is smaller than the minimal threshold {}.",
                    pos.objects().size(), publishedObjectsService.getMinimalObjectCount());
        } else {
            publicationSupport.publishAllObjects(pos.objects());
        }
    }
}
