package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ALL_RESOURCES;

@Service("keyPairRevocationManagementService")
public class KeyPairRevocationManagementServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final Logger LOG = LoggerFactory.getLogger(KeyPairRevocationManagementServiceBean.class);

    private CertificateAuthorityViewService caViewService;
    private CommandService commandService;

    @Inject
    public KeyPairRevocationManagementServiceBean(ActiveNodeService propertyService,
                                                  CertificateAuthorityViewService caViewService,
                                                  CommandService commandService) {
        super(propertyService);
        this.caViewService = caViewService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "Key Pair Revocation Management Service";
    }

    @Override
    protected void runService() {
        caViewService.findAllHostedCertificateAuthorities().stream()
                .filter(ca -> ca.getType() != ALL_RESOURCES)
                .forEach(this::revokeOldKeysFor);
    }

    private void revokeOldKeysFor(CertificateAuthorityData ca) {
        try {
            commandService.execute(new KeyManagementRevokeOldKeysCommand(ca.getVersionedId()));
        } catch (RuntimeException e) {
            LOG.error("Failed to process Certificate Authority '" + ca.getName() + "': " + e.getMessage(), e);
        }
    }
}
