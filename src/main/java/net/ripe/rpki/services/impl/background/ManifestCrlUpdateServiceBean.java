package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;

import static net.ripe.rpki.services.impl.background.BackgroundServices.MANIFEST_CRL_UPDATE_SERVICE;

@Service(MANIFEST_CRL_UPDATE_SERVICE)
@Slf4j
public class ManifestCrlUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final int manifestCrlUpdateIntervalMinutes;

    private final CommandService commandService;

    private final CertificateAuthorityRepository certificateAuthorityRepository;

    public ManifestCrlUpdateServiceBean(
        BackgroundTaskRunner backgroundTaskRunner,
        CommandService commandService,
        CertificateAuthorityRepository certificateAuthorityRepository,
        @Value("${manifest.crl.update.interval.minutes}") int manifestCrlUpdateIntervalMinutes
    ) {
        super(backgroundTaskRunner);
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
        Collection<ManagedCertificateAuthority> mustCheckForUpdatesCAs = certificateAuthorityRepository.findAllWithOutdatedManifests(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_HARD_LIMIT),
            Integer.MAX_VALUE
        );
        log.info("issuing manifests/CRLs for {} CAs with changes or where next update time exceeds hard limit", mustCheckForUpdatesCAs.size());
        processCertificateAuthorities(mustCheckForUpdatesCAs);

        // Process a limited number of CAs that are within the soft "time to next update" limit to spread out the
        // issuing of new manifests and CRLs between the soft and hard limits and avoid an 8-hourly "spike" of new
        // manifests/CRLs.
        int minutesBetweenSoftAndHardLimit = ManifestPublicationService.TIME_TO_NEXT_UPDATE.minus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT).toStandardMinutes().getMinutes();
        int estimatedCasToProcess = certificateAuthorityRepository.size() / Math.max(1, minutesBetweenSoftAndHardLimit / manifestCrlUpdateIntervalMinutes);
        Collection<ManagedCertificateAuthority> additionalCheckForUpdateCAs = certificateAuthorityRepository.findAllWithManifestsExpiringBefore(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT),
            estimatedCasToProcess
        );
        log.info("issuing manifests/CRLs for {} CAs with next update time between hard and soft limit", additionalCheckForUpdateCAs.size());
        processCertificateAuthorities(additionalCheckForUpdateCAs);
    }

    private void processCertificateAuthorities(Collection<ManagedCertificateAuthority> certificateAuthorities) {
        runParallel(certificateAuthorities.stream().map(ca -> task(
            () -> commandService.execute(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId())),
            ex -> {
                if (ex instanceof EntityNotFoundException) {
                    log.info("CA '{}' not found, probably deleted since initial query", ca.getName(), ex);
                } else {
                    log.error("Could not publish material for CA " + ca.getName(), ex);
                }
            }
        )));
    }
}
