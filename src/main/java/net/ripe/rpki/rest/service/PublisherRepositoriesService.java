package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.identity.IdentitySerializer;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponseSerializer;
import net.ripe.rpki.server.api.commands.DeleteNonHostedPublisherCommand;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.TEXT_XML;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}", produces = {APPLICATION_JSON})
@Tag(name = "/ca/{caName}", description = "Operations on CAs")
public class PublisherRepositoriesService extends AbstractCaRestService {
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final CommandService commandService;


    @Autowired
    public PublisherRepositoriesService(CertificateAuthorityViewService certificateAuthorityViewService,
                                        CommandService commandService) {
        super(false, true);
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.commandService = commandService;
    }

    @GetMapping(path = "non-hosted/publisher-repositories")
    @Operation(summary = "lists all active publisher repositories for this non-hosted CA")
    public ResponseEntity<?> listNonHostedPublicationRepositories(@PathVariable("caName") final String rawCaName) {
        try {
            CaName name = CaName.parse(rawCaName);
            Map<UUID, RepositoryResponseDto> repositories = certificateAuthorityViewService
                .findNonHostedPublisherRepositories(name.getPrincipal())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> RepositoryResponseDto.of(entry.getValue())));

            return ResponseEntity.ok().body(of("repositories", repositories));
        } catch (EntityNotFoundException e) {
            return notFoundResponse(e);
        }
    }

    @PostMapping(path = "non-hosted/publisher-repositories", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "request a publication point in the non-hosted repository, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> provisionNonHostedPublicationRepository(
        HttpServletRequest request,
        @PathVariable("caName") final String rawCaName,
        @RequestParam("file") MultipartFile file
    ) {
        log.info("Publisher request for non-hosted CA: {}", rawCaName);
        try {
            final InputStream uploadedInputStream = file.getInputStream();
            final String repositoryRequestBody = IOUtils.toString(uploadedInputStream, CHARSET);
            PublisherRequest publisherRequest = new PublisherRequestSerializer().deserialize(repositoryRequestBody);
            UUID publisherHandle = UUID.randomUUID();
            commandService.execute(new ProvisionNonHostedPublisherCommand(getVersionedId(), publisherHandle, publisherRequest));
            return ResponseEntity.created(URI.create(request.getRequestURI() + "/").resolve(publisherHandle.toString()).normalize()).build();
        } catch (EntityNotFoundException e) {
            log.warn("Non-hosted CA was not found for '{}': {}", rawCaName, e.getMessage(), e);
            return notFoundResponse(e);
        } catch (IOException | IdentitySerializer.IdentitySerializerException | IllegalArgumentException e) {
            log.warn("Could not parse uploaded certificate: {}", e.getMessage(), e);
            return ResponseEntity.status(BAD_REQUEST).body(of("error", e.getMessage()));
        }
    }

    @GetMapping(path = "non-hosted/publisher-repositories/{publisherHandle}/repository-response")
    @Operation(summary = "download the repository response for the provided publisher_handle request, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> downloadNonHostedPublicationRepositoryResponse(
        @PathVariable("caName") final String rawCaName,
        @PathVariable("publisherHandle") UUID publisherHandle
    ) {
        try {
            CaName name = CaName.parse(rawCaName);
            RepositoryResponse repositoryResponse = certificateAuthorityViewService
                .findNonHostedPublisherRepositories(name.getPrincipal())
                .get(publisherHandle);
            if (repositoryResponse == null) {
                return ResponseEntity.notFound().build();
            }

            String filename = "repository-response-" + publisherHandle + ".xml";
            String xml = new RepositoryResponseSerializer().serialize(repositoryResponse);
            return ResponseEntity.ok()
                    .header("content-disposition", "attachment; filename = " + filename)
                    .contentType(TEXT_XML)
                    .body(xml.getBytes(CHARSET));
        } catch (EntityNotFoundException e) {
            return notFoundResponse(e);
        }
    }

    private ResponseEntity<?> notFoundResponse(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(of(HttpStatus.NOT_FOUND.getReasonPhrase(), e));
    }

    @DeleteMapping(path = "non-hosted/publisher-repositories/{publisherHandle}")
    @Operation(summary = "Remove the provided publisher_handle request, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> deleteNonHostedPublicationRepository(
            @PathVariable("caName") final String rawCaName,
            @PathVariable("publisherHandle") UUID publisherHandle
                                                                 ) {
        try {
            commandService.execute(new DeleteNonHostedPublisherCommand(getVersionedId(), publisherHandle));

            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return notFoundResponse(e);
        }
    }

    @Value
    private static class RepositoryResponseDto {
        String tag;
        String serviceUri;
        String publisherHandle;
        String siaBase;
        String rrdpNotificationUri;
        String repositoryBpkiTa;

        static RepositoryResponseDto of(RepositoryResponse repositoryResponse) {
            return new RepositoryResponseDto(
                repositoryResponse.getTag().orElse(null),
                repositoryResponse.getServiceUri().toASCIIString(),
                repositoryResponse.getPublisherHandle(),
                repositoryResponse.getSiaBase().toASCIIString(),
                repositoryResponse.getRrdpNotificationUri().map(URI::toASCIIString).orElse(null),
                repositoryResponse.getRepositoryBpkiTa().getBase64String()
            );
        }
    }

}
