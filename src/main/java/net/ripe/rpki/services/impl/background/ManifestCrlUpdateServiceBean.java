package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.asAdmin;
import static net.ripe.rpki.services.impl.background.BackgroundServices.MANIFEST_CRL_UPDATE_SERVICE;

@Service(MANIFEST_CRL_UPDATE_SERVICE)
@Slf4j
public class ManifestCrlUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final int MAX_ALLOWED_EXCEPTIONS = 10;

    private final CommandService commandService;

    private final CertificateAuthorityViewService certificateAuthorityViewService;


    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public ManifestCrlUpdateServiceBean(
            ActiveNodeService propertyService,
            CommandService commandService,
            CertificateAuthorityViewService certificateAuthorityViewService) {
        super(propertyService);
        this.commandService = commandService;
        this.certificateAuthorityViewService = certificateAuthorityViewService;
    }

    @Override
    public String getName() {
        return "Public Repository Management Service";
    }

    @Override
    @SneakyThrows
    protected void runService() {
        // Once in a while run publish for each CA to deal with expiring manifests/CRLs, etc.
        final MaxExceptionsTemplate template = new MaxExceptionsTemplate(MAX_ALLOWED_EXCEPTIONS);
        List<Future<?>> tasks = new ArrayList<>();
        for (final CertificateAuthorityData ca : certificateAuthorityViewService.findAllHostedCertificateAuthorities()) {
            tasks.add(executor.submit(() -> template.wrap(new Command() {
                @Override
                public void execute() {
                    asAdmin(() -> {
                        commandService.execute(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));
                    });
                }
                @Override
                public void onException(Exception e) {
                    if (e instanceof EntityNotFoundException) {
                        log.info("CA '{}' not found, probably deleted since initial query", ca.getName(), e);
                    } else {
                        log.error("Could not publish material for CA " + ca.getName(), e);
                    }
                }
            })));
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        template.checkIfMaxExceptionsOccurred();
    }
}
