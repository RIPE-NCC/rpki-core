package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.asAdmin;

public abstract class AbstractKeyRolloverManagementServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final int MAX_ALLOWED_EXCEPTIONS = 10;

    private final Logger log;

    private final CertificationConfiguration certificationConfiguration;
    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;

    public AbstractKeyRolloverManagementServiceBean(ActiveNodeService activeNodeService,
                                                    CertificationConfiguration certificationConfiguration,
                                                    CertificateAuthorityViewService certificationService,
                                                    CommandService commandService) {
        super(activeNodeService);
        this.certificationConfiguration = certificationConfiguration;
        this.caViewService = certificationService;
        this.commandService = commandService;
        this.log = LoggerFactory.getLogger(this.getClass());
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    protected void runService(CertificateAuthorityType appliedCertificateAuthorityType) {
        final MaxExceptionsTemplate template = new MaxExceptionsTemplate(MAX_ALLOWED_EXCEPTIONS);
        final Instant oldestCreationTime = Instant.now().minus(Duration.standardDays(certificationConfiguration.getAutoKeyRolloverMaxAgeDays()));
        caViewService.findAllHostedCasWithKeyPairsOlderThan(oldestCreationTime)
                .stream()
                .filter(ca -> ca.getType() == appliedCertificateAuthorityType)
                .map(ca -> executor.submit(() -> template.wrap(new Command() {
                    @Override
                    public void execute() {
                        asAdmin(() -> {
                            commandService.execute(new KeyManagementInitiateRollCommand(ca.getVersionedId(), certificationConfiguration.getAutoKeyRolloverMaxAgeDays()));
                        });
                    }

                    @Override
                    public void onException(Exception e) {
                        log.error("Could not publish material for CA " + ca.getName(), e);
                    }
                })))
                .collect(Collectors.toList())
                .forEach(task -> {
                    try {
                        task.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        template.checkIfMaxExceptionsOccurred();
    }

}
