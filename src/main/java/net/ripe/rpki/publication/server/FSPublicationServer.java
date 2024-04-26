package net.ripe.rpki.publication.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.publication.api.PublicationWriteService;
import net.ripe.rpki.publication.persistence.disk.FileSystemPublicationObjectPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class FSPublicationServer implements PublicationWriteService {

    private static final Logger LOG = LoggerFactory.getLogger(FSPublicationServer.class);

    private final FileSystemPublicationObjectPersistence fileSystemPublicationObjectPersistence;

    private final Timer rsyncPublicationTimer;
    private final Counter rsyncPublicationSuccesses;
    private final Counter rsyncPublicationFailures;

    private volatile double publishedObjectCount = Double.NaN;

    @Inject
    public FSPublicationServer(
        FileSystemPublicationObjectPersistence fileSystemPublicationObjectPersistence,
        MeterRegistry meterRegistry) {

        this.fileSystemPublicationObjectPersistence = fileSystemPublicationObjectPersistence;

        this.rsyncPublicationTimer = Timer.builder("rpkicore.publication.timer")
            .description("time to publish to rsync")
            .tag("publication", "rsync")
            .register(meterRegistry);
        Gauge.builder("rpkicore.publication.count", () -> publishedObjectCount)
            .description("The number of objects currently published to the rsync repository")
            .tag("publication", "rsync")
            .register(meterRegistry);

        rsyncPublicationSuccesses = Counter.builder("rpkicore.publication")
            .description("The number of times the rsync repository was successfully published")
            .tag("status", "success")
            .tag("publication", "rsync")
            .register(meterRegistry);

        rsyncPublicationFailures = Counter.builder("rpkicore.publication")
            .description("The number of times the rsync repository failed to be published")
            .tag("status", "failure")
            .tag("publication", "rsync")
            .register(meterRegistry);
    }

    @Override
    public void writeAll(List<PublishedObjectData> publishedObjects) throws IOException  {
        try {
            rsyncPublicationTimer.record(() -> {
                fileSystemPublicationObjectPersistence.writeAll(publishedObjects);
            });
            publishedObjectCount = publishedObjects.size();
            rsyncPublicationSuccesses.increment();
            LOG.info("successfully published {} objects", publishedObjects.size());
        } catch (UncheckedIOException e) {
            rsyncPublicationFailures.increment();
            throw e.getCause();
        } catch (Exception e) {
            rsyncPublicationFailures.increment();
            throw e;
        }
    }
}
