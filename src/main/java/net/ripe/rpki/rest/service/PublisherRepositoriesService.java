package net.ripe.rpki.rest.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.identity.*;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.rest.exception.CaNotFoundException;
import net.ripe.rpki.rest.exception.ObjectNotFoundException;
import net.ripe.rpki.server.api.commands.DeleteNonHostedPublisherCommand;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.TEXT_XML;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}", produces = {APPLICATION_JSON})
@Tag(name = "/ca/{caName}", description = "Operations on CAs")
public class PublisherRepositoriesService extends AbstractCaRestService {
    public static final String NON_HOSTED_PUBLISHERS_ARE_NOT_AVAILABLE = "non hosted publishers are not available for this instance.";

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final CommandService commandService;
    private final Optional<NonHostedPublisherRepositoryService> maybeNonHostedPublisherRepositoryService;

    private static final ForkJoinPool krillCommunicationPool = new ForkJoinPool(4);

    /**
     * A workaround for the long-standing issue https://github.com/NLnetLabs/krill/issues/984 that appears to be a wont-fix.
     *
     * @return repository response that is patched
     */
    private static RepositoryResponse patchPublisherResponseTag(PublisherRequest publisherRequest, RepositoryResponse repositoryResponse) {
        // krill does not handle tags correctly - copy this into the response.
        //
        // >  tag:  If the <publisher_request/> message included a "tag" attribute,
        // >       the repository MUST include an identical "tag" attribute in the
        // >       <repository_response/> message; if the request did not include a
        // >       tag attribute, the response MUST NOT include a tag attribute
        // >       either.
        return new RepositoryResponse(
            publisherRequest.getTag(),
            repositoryResponse.getServiceUri(),
            repositoryResponse.getPublisherHandle(),
            repositoryResponse.getSiaBase(),
            repositoryResponse.getRrdpNotificationUri(),
            repositoryResponse.getRepositoryBpkiTa()
        );
    }


    @Autowired
    public PublisherRepositoriesService(CertificateAuthorityViewService certificateAuthorityViewService,
                                        CommandService commandService,
                                        Optional<NonHostedPublisherRepositoryService> maybeNonHostedPublisherRepositoryService) {
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.commandService = commandService;
        this.maybeNonHostedPublisherRepositoryService = maybeNonHostedPublisherRepositoryService;
    }

    @GetMapping(path = "non-hosted/publisher-repositories")
    @Operation(summary = "lists all active publisher repositories for this non-hosted CA")
    public ResponseEntity<?> listNonHostedPublicationRepositories(@PathVariable("caName") final CaName caName) {
        log.debug("List all publishers for CA: {}", caName);

        if (maybeNonHostedPublisherRepositoryService.isEmpty()) {
            return ResponseEntity.ok().body(Map.of("available", false, "repositories", Map.of()));
        }

        try {
            Map<UUID, RepositoryResponseDto> repositories = certificateAuthorityViewService
                .findNonHostedPublisherRepositories(caName.getPrincipal())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> RepositoryResponseDto.of(patchPublisherResponseTag(entry.getValue().getKey(), entry.getValue().getValue()))));

            return ResponseEntity.ok().body(Map.of("available", true, "repositories", repositories));
        } catch (EntityNotFoundException e) {
            throw new CaNotFoundException(e.getMessage());
        }
    }

    @PostMapping(path = "non-hosted/publisher-repositories", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "request a publication point in the non-hosted repository, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> provisionNonHostedPublicationRepository(
        HttpServletRequest request,
        @PathVariable("caName") final CaName caName,
        @RequestParam("file") MultipartFile file
    ) {
        log.info("Publisher request for non-hosted CA: {}", caName);

        if (maybeNonHostedPublisherRepositoryService.isEmpty()) {
            return ResponseEntity.status(NOT_ACCEPTABLE).body(NON_HOSTED_PUBLISHERS_ARE_NOT_AVAILABLE);
        }
        var nonHostedPublisherRepositoryService = this.maybeNonHostedPublisherRepositoryService.orElseThrow();

        NonHostedCertificateAuthorityData ca = getCa(NonHostedCertificateAuthorityData.class, caName);
        if (certificateAuthorityViewService.findNonHostedPublisherRepositories(ca.getName()).size() >= NonHostedCertificateAuthority.PUBLISHER_REPOSITORIES_LIMIT) {
            return ResponseEntity.status(FORBIDDEN).body(bodyForError("maximum number of publisher repositories limit exceeded"));
        }

        /*
         * Generate our own publisher handle instead of using the publisher handle in the request, since we want to
         * determine the handle to use, not the non-hosted CA. Using a random UUID will also avoid any conflicts
         * between different CAs.
         */
        UUID publisherHandle = UUID.randomUUID();
        try {
            final InputStream uploadedInputStream = file.getInputStream();
            final String repositoryRequestBody = IOUtils.toString(uploadedInputStream, StandardCharsets.UTF_8);
            PublisherRequest publisherRequest = new PublisherRequestSerializer().deserialize(repositoryRequestBody);

            // Core commands must be idempotent (and are automatically retried on transient failures) and this does not
            // work with Krill, since Krill is an external (non-transactional) system and provisioning is not idempotent.
            // So create the repository in Krill before registering the result with core.
            RepositoryResponse repositoryResponse = nonHostedPublisherRepositoryService.provisionPublisher(publisherHandle, publisherRequest, getRequestId());

            Utils.cleanupOnError(
                () -> commandService.execute(new ProvisionNonHostedPublisherCommand(
                        ca.getVersionedId(), publisherHandle, publisherRequest, repositoryResponse)),
                () -> nonHostedPublisherRepositoryService.deletePublisher(publisherHandle)
            );

            return ResponseEntity.created(URI.create(request.getRequestURI() + "/").resolve(publisherHandle.toString()).normalize()).build();
        } catch (EntityNotFoundException e) {
            log.warn("Non-hosted CA was not found for '{}': {}", caName, e.getMessage(), e);
            throw new CaNotFoundException(e.getMessage());
        } catch (CertificationResourceLimitExceededException e) {
            return ResponseEntity.status(FORBIDDEN).body(bodyForError(e.getMessage()));
        } catch (IOException | IdentitySerializer.IdentitySerializerException | IllegalArgumentException e) {
            log.warn("Could not parse uploaded certificate: {}", e.getMessage(), e);
            return badRequest(e.getMessage());
        } catch (NonHostedPublisherRepositoryService.DuplicateRepositoryException e) {
            log.warn("Could not create repository: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(bodyForError(e.getMessage()));
        }
    }

    @GetMapping(path = "non-hosted/publisher-repositories/{publisherHandle}/repository-response")
    @Operation(summary = "download the repository response for the provided publisher_handle request, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> downloadNonHostedPublicationRepositoryResponse(
        @PathVariable("caName") final CaName caName,
        @PathVariable("publisherHandle") UUID publisherHandle
    ) {
        if (maybeNonHostedPublisherRepositoryService.isEmpty()) {
            return ResponseEntity.status(NOT_ACCEPTABLE).body(NON_HOSTED_PUBLISHERS_ARE_NOT_AVAILABLE);
        }
        log.info("Download repository non-hosted publication response for CA: {}", caName);

        try {
            var publisherExchange = certificateAuthorityViewService
                .findNonHostedPublisherRepositories(caName.getPrincipal())
                .get(publisherHandle);
            if (publisherExchange == null) {
                throw new ObjectNotFoundException("publisher repository not found for handle '" + publisherHandle + "'");
            }

            String filename = "repository-response-" + publisherHandle + ".xml";
            String xml = new RepositoryResponseSerializer().serialize(patchPublisherResponseTag(publisherExchange.getKey(), publisherExchange.getValue()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .contentType(TEXT_XML)
                    .body(xml.getBytes(StandardCharsets.UTF_8));
        } catch (EntityNotFoundException e) {
            throw new CaNotFoundException(e.getMessage());
        }
    }

    @DeleteMapping(path = "non-hosted/publisher-repositories/{publisherHandle}")
    @Operation(summary = "Remove the provided publisher_handle request, see RFC8183 section 5.2.3 and 5.2.4")
    public ResponseEntity<?> deleteNonHostedPublicationRepository(
            @PathVariable("caName") final CaName caName,
            @PathVariable("publisherHandle") UUID publisherHandle
    ) {
        if (maybeNonHostedPublisherRepositoryService.isEmpty()) {
            return ResponseEntity.status(NOT_ACCEPTABLE).body(NON_HOSTED_PUBLISHERS_ARE_NOT_AVAILABLE);
        }
        var nonHostedPublisherRepositoryService = this.maybeNonHostedPublisherRepositoryService.orElseThrow();

        log.info("Delete non-hosted publication repository for CA: {}", caName);

        NonHostedCertificateAuthorityData ca = getCa(NonHostedCertificateAuthorityData.class, caName);
        try {
            commandService.execute(new DeleteNonHostedPublisherCommand(ca.getVersionedId(), publisherHandle));
            nonHostedPublisherRepositoryService.deletePublisher(publisherHandle, getRequestId());

            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            // Or just say succeeded since a deletion on a non-existing repository is idempotent?
            throw new ObjectNotFoundException("publisher repository not found for handle '" + publisherHandle + "'");
        }
    }

    @GetMapping(path = "non-hosted/publisher-content")
    @Operation(summary = "Get content for every publisher for the CA")
    public ResponseEntity<?> getPublishers(@PathVariable("caName") final CaName caName) {
        if (maybeNonHostedPublisherRepositoryService.isEmpty()) {
            return ResponseEntity.status(NOT_ACCEPTABLE).body(NON_HOSTED_PUBLISHERS_ARE_NOT_AVAILABLE);
        }
        var nonHostedPublisherRepositoryService = this.maybeNonHostedPublisherRepositoryService.orElseThrow();

        log.info("Getting full information about publishers for CA: {}", caName);

        NonHostedCertificateAuthorityData ca = getCa(NonHostedCertificateAuthorityData.class, caName);

        var nonHostedPublisherRepositories = certificateAuthorityViewService.findNonHostedPublisherRepositories(ca.getName());
        var publisherContent = krillCommunicationPool.submit(() ->
                nonHostedPublisherRepositories
                        .keySet()
                        .parallelStream()
                        .flatMap(handle -> nonHostedPublisherRepositoryService.publisherInfo(handle).stream())
                        .toList()
        ).join();

        return ResponseEntity.ok(publisherContent);
    }

    @Value
    private static class RepositoryResponseDto {
        @JsonInclude(JsonInclude.Include.NON_NULL)
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
