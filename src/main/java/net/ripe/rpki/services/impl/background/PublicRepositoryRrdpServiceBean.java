package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.handlers.PublicationSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RRDP_SERVICE;

/**
 * Publishes all RPKI objects that should be publicly available to the (RRDP) publication server.
 */
@Service(PUBLIC_REPOSITORY_RRDP_SERVICE)
public class PublicRepositoryRrdpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final PublishedObjectRepository publishedObjectRepository;
    private final PublicationSupport publicationSupport;
    private final TransactionTemplate transactionTemplate;

    public PublicRepositoryRrdpServiceBean(
            ActiveNodeService propertyService,
            PublishedObjectRepository publishedObjectRepository,
            PublicationSupport publicationSupport,
            PlatformTransactionManager transactionManager) {
        super(propertyService);
        this.publishedObjectRepository = publishedObjectRepository;
        this.publicationSupport = publicationSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read so we get a consistent snapshot of to-be-published objects
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public String getName() {
        return "Public Repository RRDP Service";
    }

    @Override
    @SneakyThrows
    protected void runService() {
        List<PublishedObjectData> publishedObjects = transactionTemplate.execute(
            (status) -> publishedObjectRepository.findCurrentlyPublishedObjects()
        );
        publicationSupport.publishAllObjects(publishedObjects);
    }
}
