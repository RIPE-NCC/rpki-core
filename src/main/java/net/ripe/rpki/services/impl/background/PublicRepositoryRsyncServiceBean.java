package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.publication.api.PublicationWriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RSYNC_SERVICE;

/**
 * Writes all published objects that should be publicly available to the local rsync repository.
 */
@Service(PUBLIC_REPOSITORY_RSYNC_SERVICE)
public class PublicRepositoryRsyncServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final PublishedObjectRepository publishedObjectRepository;
    private final PublicationWriteService publicationWriteService;
    private final TransactionTemplate transactionTemplate;

    public PublicRepositoryRsyncServiceBean(
            BackgroundTaskRunner backgroundTaskRunner,
            PublishedObjectRepository publishedObjectRepository,
            PublicationWriteService publicationWriteService,
            PlatformTransactionManager transactionManager
    ) {
        super(backgroundTaskRunner);
        this.publishedObjectRepository = publishedObjectRepository;
        this.publicationWriteService = publicationWriteService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read so we get a consistent snapshot of to-be-published objects
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public String getName() {
        return "Public Repository Rsync Service";
    }

    @Override
    @SneakyThrows
    protected void runService() {
        List<PublishedObjectData> publishedObjects = transactionTemplate.execute(
            (status) -> publishedObjectRepository.findCurrentlyPublishedObjects()
        );
        publicationWriteService.writeAll(publishedObjects);
    }
}
