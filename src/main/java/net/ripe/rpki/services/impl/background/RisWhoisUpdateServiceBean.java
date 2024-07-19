package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.bgpris.riswhois.RisWhoisFetcher;
import net.ripe.rpki.bgpris.riswhois.RisWhoisParser;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static net.ripe.rpki.services.impl.background.BackgroundServices.RIS_WHOIS_UPDATE_SERVICE;

@Slf4j
@Service(RIS_WHOIS_UPDATE_SERVICE)
public class RisWhoisUpdateServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private static final int MINIMUM_EXPECTED_UPDATES = 100000;
    static final String[] FILENAMES = {"riswhoisdump.IPv4.gz", "riswhoisdump.IPv6.gz"};

    // url -> metrics
    private final ConcurrentMap<String, RisWhoisSourceMetrics> risUpdateMetrics = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    private final BgpRisEntryViewService repository;

    private final String risWhoisBaseUrl;

    private final RisWhoisFetcher fetcher;

    public RisWhoisUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                     BgpRisEntryViewService repository,
                                     @Value("${riswhoisdump.base.url}") String risWhoisBaseUrl,
                                     RisWhoisFetcher fetcher,
                                     MeterRegistry meterRegistry) {
        super(backgroundTaskRunner);
        this.repository = repository;
        this.risWhoisBaseUrl = risWhoisBaseUrl;
        this.fetcher = fetcher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getName() {
        return "BGP RIS Update Service";
    }

    @Override
    public boolean isActive() {
        // Since we don't access the shared database and only update local memory, all nodes
        // should run this service.
        return true;
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        List<BgpRisEntry> entries = new ArrayList<>();

        AtomicLong lastUpdated = new AtomicLong(0);
        for (String filename : FILENAMES) {
            String url = risWhoisBaseUrl + "/" + filename;
            try {
                log.info("fetching RIS whois entries from {}", url);

                var result = fetcher.fetch(url);
                final Collection<BgpRisEntry> currentEntries = RisWhoisParser.parse(result.getLeft());
                updateMetrics(url, currentEntries);
                entries.addAll(currentEntries);
                lastUpdated.updateAndGet(i -> Long.max(result.getRight(), i));
            } catch (IOException | NullPointerException e) {
                log.error(String.format("Exception while handling RIS dump from %s - aborting update", url), e);
            }
        }

        if (entries.size() >= MINIMUM_EXPECTED_UPDATES) {
            log.info("fetched {} RIS whois entries.", entries.size());
            repository.resetEntries(entries);
            repository.setLastUpdated(Instant.ofEpochMilli(lastUpdated.get()));
        } else {
            log.error("Found an unusually small number of RIS whois entries, please check files at: {}", risWhoisBaseUrl);
        }
    }

    private void updateMetrics(String url, Collection<?> content) {
        risUpdateMetrics.computeIfAbsent(url, (entryUrl) -> new RisWhoisSourceMetrics(meterRegistry, entryUrl))
                .update(content);
    }

    private static class RisWhoisSourceMetrics {
        private final AtomicLong lastUpdate = new AtomicLong();
        private final AtomicLong entryCount = new AtomicLong();

        public RisWhoisSourceMetrics(MeterRegistry registry, String url) {
            Gauge.builder("riswhois.last.update.time", lastUpdate::get)
                    .description("Last update for riswhois data")
                    .tag("url", url)
                    .register(registry);

            Gauge.builder("riswhois.entries.total", entryCount::get)
                    .description("Last update for riswhois data")
                    .tag("url", url)
                    .register(registry);
        }

        public void update(Collection<?> c) {
            lastUpdate.set(Instant.now().getEpochSecond());
            entryCount.set(c.size());
        }
    }
}
