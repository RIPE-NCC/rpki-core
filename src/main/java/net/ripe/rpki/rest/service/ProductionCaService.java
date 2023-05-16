package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.security.auth.x500.X500Principal;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Positive;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Slf4j
@Scope("prototype")
@RestController
@ConditionalOnProperty(prefix="intermediate.ca", value="enabled", havingValue = "true")
@RequestMapping(path = "/prod/ca", produces = {APPLICATION_JSON})
@Tag(name = "/prod/ca", description = "Operations on Production CA")
@Validated
public class ProductionCaService {

    @Autowired
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    private CommandService commandService;

    @Autowired
    private RepositoryConfiguration certificationConfiguration;

    @PostMapping(path = "add-intermediate-cas", consumes = {APPLICATION_FORM_URLENCODED})
    @Operation(summary = "add intermediate CAs as children to production CA")
    public ResponseEntity<Object> addIntermediateCas(@RequestParam(name = "count", defaultValue = "1") @Valid @Positive @Max(100) int count) {
        log.info("Adding {} intermediate CAs", count);
        try {
            final X500Principal productionCaName = certificationConfiguration.getProductionCaPrincipal();
            final CertificateAuthorityData productionCaData = certificateAuthorityViewService.findCertificateAuthorityByName(productionCaName);

            for (int i = 0; i < count; ++i) {
                VersionedId intermediateCaId = commandService.getNextId();
                commandService.execute(new CreateIntermediateCertificateAuthorityCommand(
                    intermediateCaId,
                    certificationConfiguration.getIntermediateCaPrincipal(intermediateCaId),
                    productionCaData.getId()
                ));
            }
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to add intermediate CAs to the production CA", e);
            return Utils.badRequestError(e);
        }
    }
}
