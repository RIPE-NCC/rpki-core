package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static net.ripe.rpki.services.impl.background.BackgroundServices.KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE;

@Slf4j
@Service(KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE)
public class KeyPairActivationManagementServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final ResourceCache resourceCache;
    private final CertificationConfiguration configuration;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    public KeyPairActivationManagementServiceBean(
        ActiveNodeService propertyService,
        CertificateAuthorityViewService caViewService,
        CommandService commandService,
        ResourceCache resourceCache,
        CertificationConfiguration configuration
    ) {
        super(propertyService);
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.resourceCache = resourceCache;
        this.configuration = configuration;
    }

    @Override
    public String getName() {
        return "Key Pair Activation Management Service";
    }

    @Override
    protected void runService() {
        resourceCache.verifyResourcesArePresent();

        // Process all key activation and certificate updates from the top down by sorting the CAs by the depth
        // of the parent CA chain. This minimizes the number of RPKI objects that need to be generated and published.
        // Note that the stream operations must preserve the ordering of the CAs to make this work.
        forkJoinPool.submit(() -> {
            List<CertificateAuthorityData> casWithPendingKeys = caViewService.findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth();
            log.info("checking {} certificate authorities with pending keys for activation", casWithPendingKeys.size());

            List<CertificateAuthorityData> casWithActivatedKeys = casWithPendingKeys.parallelStream()
                .filter(ca -> {
                    CommandStatus status =
                        commandService.execute(KeyManagementActivatePendingKeysCommand.plannedActivationCommand(ca.getVersionedId(), configuration.getStagingPeriod()));
                    return status.isHasEffect();
                })
                .collect(Collectors.toList());
            log.info("activated keys for {} certificate authorities", casWithActivatedKeys.size());

            casWithActivatedKeys.forEach(parentCA -> {
                Collection<CertificateAuthorityData> children = caViewService.findAllChildrenForCa(parentCA.getName());
                children.parallelStream()
                    .forEach(childCA -> {
                        try {
                            switch (childCA.getType()) {
                                case ALL_RESOURCES:
                                    throw new IllegalStateException("CA with type ALL_RESOURCES (" + childCA + ") should not be a child of " + parentCA);
                                case ROOT: case HOSTED: case NONHOSTED:
                                    commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(childCA.getVersionedId(), Integer.MAX_VALUE));
                                    break;
                                default:
                                    throw new IllegalStateException("CA with unknown type " + childCA.getType());
                            }
                        } catch (RuntimeException e) {
                            log.error("Error updating incoming resource certificates for CA '{}'", childCA.getName(), e);
                        }
                    });
            });
        }).join();
    }
}
