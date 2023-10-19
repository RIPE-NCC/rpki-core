package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;

@Service(ALL_CA_CERTIFICATE_UPDATE_SERVICE)
public class AllCaCertificateUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private final int updateBatchSize;

    private final Counter certificateUpdates;

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final ResourceCache resourceCache;
    private final RepositoryConfiguration repositoryConfiguration;


    public AllCaCertificateUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                             CertificateAuthorityViewService caViewService,
                                             CommandService commandService,
                                             ResourceCache resourceCache,
                                             RepositoryConfiguration repositoryConfiguration,
                                             @Value("${certificate.authority.update.batch.size:1000}") int updateBatchSize,
                                             MeterRegistry meterRegistry) {
        super(backgroundTaskRunner);
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.resourceCache = resourceCache;
        this.repositoryConfiguration = repositoryConfiguration;
        this.updateBatchSize = updateBatchSize;

        certificateUpdates = Counter.builder("rpkicore.all.certificate.update.progress")
                .description("Number of certificates updated by All CA certificate update service")
                .register(meterRegistry);
    }

    @Override
    public String getName() {
        return "All CA certificate update service";
    }

    @Override
    public Map<String, String> supportedParameters() {
        return Collections.singletonMap(BATCH_SIZE_PARAMETER, String.valueOf(this.updateBatchSize));
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        runService(parameters, x -> true);
    }

    public void runService(Map<String, String> parameters, Predicate<CertificateAuthorityData> certificateAuthorityFilter) {
        CertificateAuthorityData productionCa = verifyPreconditions();
        if (productionCa == null) {
            return;
        }

        int batchSize = parseBatchSizeParameter(parameters).orElse(this.updateBatchSize);
        log.info("Updating incoming certificate for at most {} CAs", batchSize);

        AtomicInteger remainingCounter = new AtomicInteger(batchSize);
        new RecursiveUpdater(remainingCounter, certificateAuthorityFilter).accept(productionCa);
    }

    private CertificateAuthorityData verifyPreconditions() {
        resourceCache.verifyResourcesArePresent();

        CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
        if (allResourcesCa == null) {
            log.error("All Resources Certificate Authority '{}' was not found.", repositoryConfiguration.getAllResourcesCaPrincipal().getName());
            return null;
        }

        final X500Principal productionCaPrincipal = repositoryConfiguration.getProductionCaPrincipal();
        CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        if (productionCa == null) {
            log.error("Production Certificate Authority '{}' not found.", productionCaPrincipal);
            return null;
        }
        return productionCa;
    }

    @AllArgsConstructor
    private class RecursiveUpdater implements Consumer<CertificateAuthorityData> {
        private AtomicInteger remainingCounter;
        private Predicate<CertificateAuthorityData> certificateAuthorityFilter;

        @Override
        public void accept(CertificateAuthorityData ca) {
            runParallel(Stream.of(updateParentAndChildrenTask(ca)));
        }

        /**
         * A task that updates the incoming resource certificate of the <code>parentCa</code> and its child CAs, recursively.
         * The task will return <code>true</code> if any CA was updated, <code>false</code> otherwise.
         * The task will stop when more than <code>updateBatchSize</code> CAs have been updated (due to concurrency slightly more
         * CAs may get updated than specified).
         */
        private BackgroundTaskRunner.Task<Boolean> updateParentAndChildrenTask(CertificateAuthorityData parentCa) {
            return task(
                () -> {
                    if (remainingCounter.get() <= 0) {
                        return false;
                    }

                    if (!certificateAuthorityFilter.test(parentCa)) {
                        return false;
                    }

                    // NOTE: There's no update of potentially over-claiming CAs happening here,
                    // since we are updating all child CAs anyway.
                    boolean updated = updateIncomingCertificates(parentCa);
                    if (updated) {
                        remainingCounter.decrementAndGet();
                        certificateUpdates.increment();
                    }

                    long updateCount = updateChildren(parentCa);
                    if (updateCount > 0) {
                        // Update the parent CA again, in case over-claiming child certificates were updated to correctly
                        // remove the over-claiming resources.
                        updateIncomingCertificates(parentCa);
                    }

                    return updated || updateCount > 0;
                },
                ex -> log.error("Unable to update incoming resource certificate for CA '{}'", parentCa.getName(), ex)
            );
        }

        private long updateChildren(CertificateAuthorityData parentCa) {
            Collection<CertificateAuthorityData> childCas = caViewService.findAllChildrenForCa(parentCa.getName());
            return runParallel(
                childCas.stream().map(this::updateParentAndChildrenTask)
            ).stream().filter(Boolean::booleanValue).count();
        }

        private boolean updateIncomingCertificates(CertificateAuthorityData ca) {
            try {
                return commandService
                    .execute(new UpdateAllIncomingResourceCertificatesCommand(ca.getVersionedId(), Integer.MAX_VALUE))
                    .isHasEffect();
            } catch (EntityNotFoundException e) {
                // CA was deleted between the initial query and executing the command, ignore this exception. Note that the
                // command service already logs a warning, so no need to log anything else here.
                log.warn("failed to update all incoming resource certificates for CA '{}': {}", ca.getName(), e.toString());
                return false;
            }
        }
    }
}
