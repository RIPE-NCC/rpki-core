package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorRequestSerializer;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorResponseSerializer;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.security.auth.x500.X500Principal;
import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = "/api/upstream", produces = {APPLICATION_JSON})
@Tag(name = "/api/upstream", description = "Operations on TA")
public class UpstreamCaService extends RestService{
    private final RepositoryConfiguration configuration;

    private final CommandService commandService;
    private final CertificateAuthorityViewService caViewService;

    private final BackgroundService allCertificateUpdateService;

    @Autowired
    public UpstreamCaService(RepositoryConfiguration configuration,
                             CertificateAuthorityViewService caViewService,
                             CommandService commandService,
                             BackgroundService allCertificateUpdateService) {

        this.caViewService = caViewService;
        this.commandService = commandService;
        this.configuration = configuration;
        this.allCertificateUpdateService = allCertificateUpdateService;
    }

    @PostMapping(path = "response", consumes = {MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_FORM_URLENCODED})
    @Operation(summary = "Upload TA response")
    public ResponseEntity<?> postTaResponse(@RequestParam("file") MultipartFile file) {
        return uploadTaResponse(file);
    }

    @PostMapping(path = "upload", consumes = {MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_FORM_URLENCODED})
    @Operation(summary = "Upload TA response (legacy)")
    public ResponseEntity<?> uploadTaResponse(@RequestParam("file") MultipartFile file) {
        log.info("Uploaded the TA response");
        try {
            final String content = IOUtils.toString(file.getInputStream(), StandardCharsets.UTF_8);
            final TrustAnchorResponse responseObject = new TrustAnchorResponseSerializer().deserialize(content);

            X500Principal allResourcesCaName = configuration.getAllResourcesCaPrincipal();
            CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(allResourcesCaName);

            CompletableFuture.runAsync(() -> {
                commandService.execute(new ProcessTrustAnchorResponseCommand(allResourcesCa.getVersionedId(), responseObject));
                allCertificateUpdateService.execute(Collections.emptyMap());
            });

            return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("Uploaded successfully and processing");

        } catch (Exception e) {
            log.error("Could not parse or apply uploaded TA response {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(of("error", e.getMessage()));
        }
    }

    @PostMapping(path = "request")
    @Operation(summary = "Download TA request")
    public ResponseEntity<?> createTaRequest() {
        log.info("Returning TA response");
        try {
            X500Principal allResourcesCaName = configuration.getAllResourcesCaPrincipal();
            CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(allResourcesCaName);
            if (allResourcesCa == null) {
                return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(of("error", "All Resources CA doesn't exist"));
            }
            if (allResourcesCa.getTrustAnchorRequest() == null) {
                log.info("No current TA signing request, creating one");
                commandService.execute(new AllResourcesCaResourcesCommand(allResourcesCa.getVersionedId()));
                allResourcesCa = caViewService.findCertificateAuthorityByName(allResourcesCaName);
            }
            final String request = new TrustAnchorRequestSerializer().serialize(allResourcesCa.getTrustAnchorRequest());

            return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                .body(request);

        } catch (Exception e) {
            log.error("Could not create TA request {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(of("error", e.getMessage()));
        }
    }
}
