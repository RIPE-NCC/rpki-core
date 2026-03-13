package net.ripe.rpki.services.impl.background;

import lombok.Getter;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Getter
public class PublishedObjectsServiceBean {

    private final int minimalObjectCount;
    private final TransactionTemplate transactionTemplate;
    private final PublishedObjectRepository publishedObjectRepository;
    private final AtomicBoolean publicationBelowThreshold = new AtomicBoolean(false);
    private final boolean checkThreshold;

    protected PublishedObjectsServiceBean(
            PublishedObjectRepository publishedObjectRepository,
            PlatformTransactionManager transactionManager,
            @Value("${publication.thresholds.minimum_object_count:0}") int minimalObjectCount,
            @Value("${publication.thresholds.enabled:false}") boolean checkThreshold
    ) {
        this.publishedObjectRepository = publishedObjectRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read so we get a consistent snapshot of to-be-published objects
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.minimalObjectCount = minimalObjectCount;
        this.checkThreshold = checkThreshold;
        if (this.checkThreshold && this.minimalObjectCount <= 0) {
            throw new IllegalArgumentException("Minimal object count must be greater than zero when publication threshold checking is enabled.");
        }
    }

    public PublishedObjects getPublishedObjects() {
        List<PublishedObjectData> publishedObjects = transactionTemplate.execute(
                status -> publishedObjectRepository.findCurrentlyPublishedObjects()
        );
        if (checkThreshold) {
            var isBelowThreshold = publishedObjects.size() < minimalObjectCount;
            publicationBelowThreshold.set(isBelowThreshold);
            return new PublishedObjects(publishedObjects, isBelowThreshold);
        }
        return new PublishedObjects(publishedObjects, false);
    }

    public record PublishedObjects(List<PublishedObjectData> objects, boolean isBelowThreshold) {
    }
}
