package net.ripe.rpki.rest.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.security.auth.x500.X500Principal;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@AllArgsConstructor
@Slf4j
@RestController
@ConditionalOnProperty(prefix="system.setup.and.testing.api", value="enabled", havingValue = "true")
@RequestMapping(path = "/prod/ca", produces = {APPLICATION_JSON})
@Validated
public class SystemSetupService {
    @Autowired
    private ActiveNodeService activeNodeService;

    @Autowired
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    private CommandService commandService;

    @Autowired
    private RepositoryConfiguration certificationConfiguration;

    @PostMapping(path = "create")
    public ResponseEntity<Object> create() {
        log.info("Creating production CA");
        try {
            VersionedId caId = commandService.getNextId();
            commandService.execute(new CreateRootCertificateAuthorityCommand(caId, certificationConfiguration.getProductionCaPrincipal()));
            activeNodeService.activateCurrentNode();

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to create production CA", e);
            return Utils.badRequestError(e);
        }
    }

    @PostMapping(path = "generate-all-resources-sign-request")
    public ResponseEntity<Object> generateAllResourcesSignRequest() {
        log.info("Creating all resources CA");
        try {
            final X500Principal productionCaName = certificationConfiguration.getProductionCaPrincipal();
            final CertificateAuthorityData productionCaData = certificateAuthorityViewService.findCertificateAuthorityByName(productionCaName);

            commandService.execute(new AllResourcesCaResourcesCommand(productionCaData.getVersionedId()));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to create all resources CA", e);
            return Utils.badRequestError(e);
        }
    }
}