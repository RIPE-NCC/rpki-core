package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ALL_RESOURCES;

@Service("keyPairRevocationManagementService")
public class KeyPairRevocationManagementServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;

    @Inject
    public KeyPairRevocationManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                  CertificateAuthorityViewService caViewService,
                                                  CommandService commandService) {
        super(backgroundTaskRunner);
        this.caViewService = caViewService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "Key Pair Revocation Management Service";
    }

    @Override
    protected void runService() {
        runParallel(caViewService.findAllHostedCertificateAuthorities().stream()
                .filter(ca -> ca.getType() != ALL_RESOURCES)
                .map(ca -> task(
                    () -> commandService.execute(new KeyManagementRevokeOldKeysCommand(ca.getVersionedId())),
                    ex -> log.error("Failed to process CA '{}'", ca.getName(), ex)
                ))
        );
    }

}
