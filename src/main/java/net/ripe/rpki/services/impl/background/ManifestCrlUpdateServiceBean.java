package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.asAdmin;
import static net.ripe.rpki.services.impl.background.BackgroundServices.MANIFEST_CRL_UPDATE_SERVICE;

@Service(MANIFEST_CRL_UPDATE_SERVICE)
@Slf4j
public class ManifestCrlUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final int MAX_ALLOWED_EXCEPTIONS = 10;

    private final int manifestCrlUpdateIntervalMinutes;

    private final CommandService commandService;

    private final CertificateAuthorityRepository certificateAuthorityRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public ManifestCrlUpdateServiceBean(
        ActiveNodeService propertyService,
        CommandService commandService,
        CertificateAuthorityRepository certificateAuthorityRepository,
        @Value("${manifest.crl.update.interval.minutes}") int manifestCrlUpdateIntervalMinutes
    ) {
        super(propertyService);
        this.commandService = commandService;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.manifestCrlUpdateIntervalMinutes = manifestCrlUpdateIntervalMinutes;
    }

    @Override
    public String getName() {
        return "Manifest and CRL Update Service";
    }

    @Override
    @SneakyThrows
    protected void runService() {
        // Process all CAs with pending publications and a next update time within the hard limit "time to next update"
        Collection<HostedCertificateAuthority> mustCheckForUpdatesCAs = certificateAuthorityRepository.findAllWithOutdatedManifests(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_HARD_LIMIT)
        );
        log.info("issuing manifests/CRLs for {} CAs with changes or where next update time exceeds hard limit", mustCheckForUpdatesCAs.size());
        processCertificateAuthorities(mustCheckForUpdatesCAs);

        // Process a limited number of CAs that are within the soft "time to next update" limit to spread out the
        // issuing of new manifests and CRLs between the soft and hard limits and avoid an 8-hourly "spike" of new
        // manifests/CRLs.
        int minutesBetweenSoftAndHardLimit = CertificateManagementServiceImpl.TIME_TO_NEXT_UPDATE.minus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT).toStandardMinutes().getMinutes();
        int estimatedCasToProcess = certificateAuthorityRepository.size() / Math.max(1, minutesBetweenSoftAndHardLimit / manifestCrlUpdateIntervalMinutes);
        Collection<HostedCertificateAuthority> additionalCheckForUpdateCAs = certificateAuthorityRepository.findAllWithManifestsExpiringBefore(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT),
            estimatedCasToProcess
        );
        log.info("issuing manifests/CRLs for {} CAs with next update time between hard and soft limit", additionalCheckForUpdateCAs.size());
        processCertificateAuthorities(additionalCheckForUpdateCAs);
    }

    private void processCertificateAuthorities(Collection<HostedCertificateAuthority> certificateAuthorities) throws InterruptedException, ExecutionException {
        final MaxExceptionsTemplate template = new MaxExceptionsTemplate(MAX_ALLOWED_EXCEPTIONS);
        List<Future<?>> tasks = new ArrayList<>();
        for (final CertificateAuthority ca : certificateAuthorities) {
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
