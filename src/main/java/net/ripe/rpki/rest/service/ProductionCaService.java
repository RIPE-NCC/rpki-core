package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundQueuedTaskRunner;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.MigrateMemberCertificateAuthorityToIntermediateParentCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private BackgroundTaskRunner backgroundTaskRunner;

    @Autowired
    private SequentialBackgroundQueuedTaskRunner sequentialBackgroundQueuedTaskRunner;

    @Value("${intermediate.ca.enabled:false}")
    boolean intermediateCaEnabled;

    @PostMapping(path = "add-intermediate-cas", consumes = {APPLICATION_FORM_URLENCODED})
    @Operation(summary = "add intermediate CAs as children to production CA")
    public ResponseEntity<Object> addIntermediateCas(@RequestParam(name = "count", defaultValue = "1") @Valid @Positive @Max(100) int count) {
        if (!intermediateCaEnabled) {
            return ResponseEntity.notFound().build();
        }

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

    @PostMapping(path = "migrate-member-cas-to-intermediate-cas", consumes = {APPLICATION_FORM_URLENCODED})
    @Operation(summary = "migrate member CAs from the production CA to the intermediate CAs")
    public ResponseEntity<Object> migrateMemberCasToIntermediateCas(@RequestParam(name = "count", defaultValue = "1") @Valid @Positive @Max(1000) int count) {
        if (!intermediateCaEnabled) {
            return ResponseEntity.notFound().build();
        }

        try {
            final X500Principal productionCaName = certificationConfiguration.getProductionCaPrincipal();
            Collection<CertificateAuthorityData> productionCaChildren = certificateAuthorityViewService.findAllChildrenForCa(productionCaName);
            List<CertificateAuthorityData> intermediateCas = productionCaChildren.stream().filter(ca -> ca.getType() == CertificateAuthorityType.INTERMEDIATE).collect(Collectors.toList());
            if (intermediateCas.isEmpty()) {
                log.error("No intermediate CAs found");
                return Utils.badRequestError("no intermediate CAs found");
            }

            List<CertificateAuthorityData> memberCasToMigrate = productionCaChildren.stream()
                .filter(ca -> ca.getType() == CertificateAuthorityType.HOSTED || ca.getType() == CertificateAuthorityType.NONHOSTED)
                .limit(count)
                .collect(Collectors.toList());

            if (memberCasToMigrate.isEmpty()) {
                log.info("No member CAs to migrate found");
                return ResponseEntity.noContent().build();
            }

            Map<CertificateAuthorityData, List<CertificateAuthorityData>> memberCasGroupedByNewParent = memberCasToMigrate
                .stream()
                .collect(Collectors.groupingBy(ca -> intermediateCas.get(Math.abs((int) (ca.getUuid().getLeastSignificantBits() % intermediateCas.size())))));

            sequentialBackgroundQueuedTaskRunner.submit("migrate member CAs to intermediate CA parent", () -> {
                    log.info("Migrating {} member CAs from the production CA to {} intermediate CAs", memberCasToMigrate.size(), intermediateCas.size());

                    for (Map.Entry<CertificateAuthorityData, List<CertificateAuthorityData>> group : memberCasGroupedByNewParent.entrySet()) {
                        CertificateAuthorityData newParent = group.getKey();
                        List<CertificateAuthorityData> memberCaGroup = group.getValue();
                        backgroundTaskRunner.runParallel(
                            memberCaGroup.stream().map(memberCa -> backgroundTaskRunner.task(
                                () -> commandService.execute(new MigrateMemberCertificateAuthorityToIntermediateParentCommand(memberCa.getVersionedId(), newParent.getId())),
                                e -> log.error("error migrating member CA '{}' to new intermediate CA parent {}", memberCa.getName(), newParent.getName())
                            ))
                        );
                    }

                    log.info("Migrated {} member CAs", memberCasToMigrate.size());
                },
                e -> {
                    throw new RuntimeException(e);
                }
            );

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to add intermediate CAs to the production CA", e);
            return Utils.badRequestError(e);
        }
    }
}
