package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
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

    @Inject
    public ReinitServiceBean(ActiveNodeService activeNodeService,
                             CommandService commandService,
                             CertificateAuthorityViewService caViewService,
                             RepositoryConfiguration repositoryConfiguration) {
        super(activeNodeService);
        this.commandService = commandService;
        this.caViewService = caViewService;
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
        final CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        if (productionCa == null) {
            commandService.execute(new CreateRootCertificateAuthorityCommand(commandService.getNextId()));
            log.info("Created Production CA {}.", allResourcesCaPrincipal);
        } else {
            log.warn("Production Certificate Authority '{}' already exists, will not try to re-create it.", productionCaPrincipal);
        }
    }

    @Override
    public String getName() {
        return "Create necessary CAs: All Resources CAs and Production CA";
    }
}
