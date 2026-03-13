package net.ripe.rpki.services.impl.handlers;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Getter
public class PublicationMetrics {

    private static final String RPKICORE_PUBLICATION_TOTAL = "rpkicore.publication.total";
    private static final String RPKICORE_PUBLICATION = "rpkicore.publication";
    private static final String STATUS = "status";
    private static final String SUCCESS = "success";
    private static final String FAILED = "failed";
    private static final String FAILURE = "failure";
    private static final String UNDER_THRESHOLD = "under_threshold";
    private static final String PUBLICATION = "publication";
    private static final String RRDP = "rrdp";
    private static final String RSYNC = "rsync";

    private final MeterRegistry meterRegistry;

    private final Counter rrdpPublicationSuccesses;
    private final Counter rrdpPublicationFailures;
    private final Counter rrdpPublicationUnderThreshold;
    private final Timer rsyncPublicationTimer;
    private final Counter rsyncPublicationSuccesses;
    private final Counter rsyncPublicationFailures;
    private final Counter rsyncPublicationUnderThreshold;

    private final AtomicDouble publishedObjectCount = new AtomicDouble(Double.NaN);

    @Inject
    public PublicationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        rrdpPublicationSuccesses = Counter.builder(RPKICORE_PUBLICATION_TOTAL)
                .description("The total number of successful RRDP publications")
                .tag(STATUS, SUCCESS)
                .tag(PUBLICATION, RRDP)
                .register(meterRegistry);

        rrdpPublicationFailures = Counter.builder(RPKICORE_PUBLICATION_TOTAL)
                .description("The total number of failed RRDP publications")
                .tag(STATUS, FAILED)
                .tag(PUBLICATION, RRDP)
                .register(meterRegistry);

        rrdpPublicationUnderThreshold = Counter.builder(RPKICORE_PUBLICATION_TOTAL)
                .description("The number of times RRDP publication failed because the number of published objects was under the threshold")
                .tag(STATUS, UNDER_THRESHOLD)
                .tag(PUBLICATION, RRDP)
                .register(meterRegistry);

        rsyncPublicationTimer = Timer.builder("rpkicore.publication.timer")
                .description("time to publish to rsync")
                .tag(PUBLICATION, RSYNC)
                .register(meterRegistry);

        Gauge.builder("rpkicore.publication.count", publishedObjectCount::get)
                .description("The number of objects currently published to the rsync repository")
                .tag(PUBLICATION, RSYNC)
                .register(meterRegistry);

        rsyncPublicationSuccesses = Counter.builder(RPKICORE_PUBLICATION)
                .description("The number of times the rsync repository was successfully published")
                .tag(STATUS, SUCCESS)
                .tag(PUBLICATION, RSYNC)
                .register(meterRegistry);

        rsyncPublicationFailures = Counter.builder(RPKICORE_PUBLICATION)
                .description("The number of times the rsync repository failed to be published")
                .tag(STATUS, FAILURE)
                .tag(PUBLICATION, RSYNC)
                .register(meterRegistry);

        rsyncPublicationUnderThreshold = Counter.builder(RPKICORE_PUBLICATION)
                .description("The number of times publication to the rsync repository failed because the number of published objects was under the threshold")
                .tag(STATUS, UNDER_THRESHOLD)
                .tag(PUBLICATION, RSYNC)
                .register(meterRegistry);
    }

    public void setPublishedObjectCount(int size) {
        this.publishedObjectCount.set(size);
    }
}
