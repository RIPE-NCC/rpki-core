package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.InitialiseMyIdentityMaterialCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;

@Service("reinitServiceBean")
@Profile("!production & !pilot")
@Slf4j
public class ReinitServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CommandService commandService;
    private final CertificateAuthorityViewService caViewService;
    private final RepositoryConfiguration repositoryConfiguration;

    private final ProvisioningIdentityViewService provisioningIdentityViewService;

    @Inject
    public ReinitServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                             CommandService commandService,
                             CertificateAuthorityViewService caViewService,
                             ProvisioningIdentityViewService provisioningIdentityViewService,
                             RepositoryConfiguration repositoryConfiguration) {
        super(backgroundTaskRunner);
        this.commandService = commandService;
        this.caViewService = caViewService;
        this.provisioningIdentityViewService = provisioningIdentityViewService;
        this.repositoryConfiguration = repositoryConfiguration;
    }

    @Override
    protected void runService() {
        final X500Principal allResourcesCaPrincipal = repositoryConfiguration.getAllResourcesCaPrincipal();
        final CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(allResourcesCaPrincipal);
        if (allResourcesCa == null) {
            commandService.execute(new CreateAllResourcesCertificateAuthorityCommand(commandService.getNextId()));
            log.info("Created All Resources CA {}.", allResourcesCaPrincipal);
        } else {
            log.warn("All Resources CA {} already exists, will not try to re-create it.", allResourcesCaPrincipal);
        }

        final X500Principal productionCaPrincipal = repositoryConfiguration.getProductionCaPrincipal();
        final CertificateAuthorityData maybeProductionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        if (maybeProductionCa == null) {
            commandService.execute(new CreateRootCertificateAuthorityCommand(commandService.getNextId()));
            log.info("Created Production CA {}.", productionCaPrincipal);
        } else {
            log.warn("Production Certificate Authority '{}' already exists, will not try to re-create it.", productionCaPrincipal);
        }

        final CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        ProvisioningIdentityCertificate identityMaterial = provisioningIdentityViewService.findProvisioningIdentityMaterial();

        if (identityMaterial == null) {
            commandService.execute(new InitialiseMyIdentityMaterialCommand(productionCa.getVersionedId()));
        } else {
            log.warn("Provisioning identity certificate (for up-down communication) already exist.");
        }
    }

    @Override
    public String getName() {
        return "Create necessary CAs: All Resources CAs and Production CA and up-down key material";
    }
}
