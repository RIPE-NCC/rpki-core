package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.CA_CLEAN_UP_SERVICE;

@Service(CA_CLEAN_UP_SERVICE)
public class CaCleanUpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final CommandService commandService;
    private final Counter deletedCasWithoutKeyPairsCounter;
    @Getter
    private final boolean enabled;

    @Inject
    public CaCleanUpServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                CertificateAuthorityRepository certificateAuthorityRepository,
                                CommandService commandService,
                                MeterRegistry meterRegistry,
                                @Value("${certificate.authority.cleanup.service.enabled:false}") boolean enabled) {
        super(backgroundTaskRunner);
        this.enabled = enabled;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.commandService = commandService;

        this.deletedCasWithoutKeyPairsCounter = Counter.builder("rpkicore.deleted.ca.without.key.pairs")
            .description("The number of deleted CAs without active key pairs")
            .register(meterRegistry);
    }

    @Override
    public boolean isActive() {
        return enabled;
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        if (enabled) {
            final Collection<ManagedCertificateAuthority> casToDelete = certificateAuthorityRepository.getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear();
            deletedCasWithoutKeyPairsCounter.increment(casToDelete.size());
            casToDelete.forEach(ca -> commandService.execute(new DeleteCertificateAuthorityCommand(ca.getVersionedId(), ca.getName())));
            log.info("Deleted {} CAs without active key pair for more than a year", casToDelete.size());
        } else {
            log.warn("The service {} is disabled.", getName());
        }
    }

    @Override
    public String getName() {
        return "Cleanup CAs without activity and key pairs";
    }
}
