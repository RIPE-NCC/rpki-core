package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.Duration;
import org.joda.time.Instant;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractKeyRolloverManagementServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CertificationConfiguration certificationConfiguration;
    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;

    private final Optional<Integer> defaultBatchSize;

    protected AbstractKeyRolloverManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                       CertificationConfiguration certificationConfiguration,
                                                       CertificateAuthorityViewService certificationService,
                                                       CommandService commandService,
                                                       Optional<Integer> defaultBatchSize) {
        super(backgroundTaskRunner);
        this.certificationConfiguration = certificationConfiguration;
        this.caViewService = certificationService;
        this.commandService = commandService;
        this.defaultBatchSize = defaultBatchSize;
    }

    protected void runKeyRoll(Class<? extends ManagedCertificateAuthority> type, Map<String, String> parameters) {
        Optional<Integer> actualBatchSize;
        try {
            actualBatchSize = parseBatchSizeParameter(parameters).or(() -> this.defaultBatchSize);
        } catch (IllegalArgumentException e) {
            log.warn("error parsing batch size parameter: {}", e.getMessage());
            return;
        }

        actualBatchSize.ifPresent(size -> log.info("initiating key roll for up to {} certificate authorities of type {}", size, type.getSimpleName()));

        final Instant oldestCreationTime = Instant.now().minus(
            Duration.standardDays(certificationConfiguration.getAutoKeyRolloverMaxAgeDays()));
        runParallel(caViewService.findManagedCasEligibleForKeyRoll(type, oldestCreationTime, actualBatchSize)
            .stream()
            .map(ca -> task(
                () -> commandService.execute(new KeyManagementInitiateRollCommand(ca.getVersionedId(), certificationConfiguration.getAutoKeyRolloverMaxAgeDays())),
                ex -> log.error("Could not publish material for CA {}", ca.getName(), ex)
            )));
    }

    @Override
    public Map<String, String> supportedParameters() {
        return defaultBatchSize
            .map(x -> Collections.singletonMap(BATCH_SIZE_PARAMETER, x.toString()))
            .orElse(Collections.emptyMap());
    }
}
