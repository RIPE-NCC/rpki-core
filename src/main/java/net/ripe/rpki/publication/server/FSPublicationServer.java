package net.ripe.rpki.publication.server;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.publication.api.PublicationWriteService;
import net.ripe.rpki.publication.persistence.disk.FileSystemPublicationObjectPersistence;
import net.ripe.rpki.services.impl.handlers.PublicationMetrics;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
@Slf4j
public class FSPublicationServer implements PublicationWriteService {

    private final FileSystemPublicationObjectPersistence fileSystemPublicationObjectPersistence;
    private final PublicationMetrics publicationMetrics;

    @Inject
    public FSPublicationServer(FileSystemPublicationObjectPersistence fileSystemPublicationObjectPersistence,
                               PublicationMetrics publicationMetrics) {
        this.fileSystemPublicationObjectPersistence = fileSystemPublicationObjectPersistence;
        this.publicationMetrics = publicationMetrics;
    }

    @Override
    public void writeAll(List<PublishedObjectData> publishedObjects) throws IOException {
        try {
            publicationMetrics.getRsyncPublicationTimer().record(() ->
                    fileSystemPublicationObjectPersistence.writeAll(publishedObjects));
            publicationMetrics.setPublishedObjectCount(publishedObjects.size());
            publicationMetrics.getRsyncPublicationSuccesses().increment();
            log.info("successfully published {} objects", publishedObjects.size());
        } catch (UncheckedIOException e) {
            publicationMetrics.getRsyncPublicationFailures().increment();
            throw e.getCause();
        } catch (Exception e) {
            publicationMetrics.getRsyncPublicationFailures().increment();
            throw e;
        }
    }
}
