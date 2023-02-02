package net.ripe.rpki.rest.service;

import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.provisioning.identity.*;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.rest.pojo.RevokeHostedResult;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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

import javax.security.auth.x500.X500Principal;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.TEXT_XML;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}", produces = {APPLICATION_JSON})
@Tag(name = "/ca/{caName}", description = "Operations on CAs")
public class CaService extends AbstractCaRestService {
    private static final Charset CHARSET_NAME = StandardCharsets.UTF_8;

    private final CertificateAuthorityCreateService certificateAuthorityCreateService;
    private final ResourceLookupService resourceCache;
    private final CommandService commandService;
    private final ProvisioningIdentityViewService delegationCaProvisioningService;

    @Autowired
    public CaService(CertificateAuthorityCreateService certificateAuthorityCreateService,
                     ResourceLookupService resourceCache,
                     CommandService commandService,
                     ProvisioningIdentityViewService delegationCaProvisioningService) {
        this.certificateAuthorityCreateService = certificateAuthorityCreateService;
        this.resourceCache = resourceCache;
        this.commandService = commandService;
        this.delegationCaProvisioningService = delegationCaProvisioningService;
    }

    private ResponseEntity<Map<String,String>> responseForCaNameNotUniqueException(CaName caName) {
        return ResponseEntity.status(CONFLICT)
                .body(bodyForError(String.format("CA '%s' already exists.", caName)));
    }

    @PostMapping(path = "hosted")
    @Operation(summary = "Create hosted CA")
    public ResponseEntity<?> createHosted(@PathVariable("caName") final CaName caName) {
        log.info("Creating hosted CA: {}", caName);
        try {
            certificateAuthorityCreateService.createHostedCertificateAuthority(caName.getPrincipal());
            return created();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (CertificateAuthorityNameNotUniqueException e) {
            log.warn("CA was already provisioned for '{}': {}", caName, e.getMessage());
            return responseForCaNameNotUniqueException(caName);
        }
    }

    @PostMapping(path = "non-hosted", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Create non-hosted CA")
    public ResponseEntity<?> createNonHosted(@PathVariable("caName") final CaName caName,
                                             @RequestParam("file") MultipartFile file) {
        log.info("Creating non-hosted CA: {}", caName);
        try {
            final InputStream uploadedInputStream = file.getInputStream();
            certificateAuthorityCreateService.createNonHostedCertificateAuthority(
                    caName.getPrincipal(), parseCertificate(uploadedInputStream));
            log.info("Has created CA: {}", caName);
            return created();
        } catch (CertificateAuthorityNameNotUniqueException e) {
            log.warn("CA was already provisioned for '{}': {}", caName, e.getMessage());
            return responseForCaNameNotUniqueException(caName);
        } catch (IdentitySerializer.IdentitySerializerException | IOException | IllegalArgumentException e) {
            log.warn("Could not parse uploaded certificate: {}", e.getMessage(), e);
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping(path = "non-hosted")
    @Operation(summary = "Revoke non-hosted CA")
    public ResponseEntity<?> revokeNonHosted(@PathVariable("caName") final CaName caName) {
        log.info("Revoking non-hosted CA: {}", caName);
        NonHostedCertificateAuthorityData ca = getCa(NonHostedCertificateAuthorityData.class, caName);
        try {
            commandService.execute(new DeleteCertificateAuthorityCommand(ca.getVersionedId(), ca.getName()));
            log.info("Revoked non-hosted CA: {}", caName);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            log.error("Error while revoking non-hosted CA", e);
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping(path = "hosted")
    @Operation(summary = "Revoke hosted CA")
    public ResponseEntity<RevokeHostedResult> revokeHosted(@PathVariable("caName") final CaName caName) {
        log.info("Revocation attempt for hosted CA '{}'", caName);
        final ManagedCertificateAuthorityData certificateAuthority = getCa(ManagedCertificateAuthorityData.class, caName);

        if (certificateAuthority.getType() != CertificateAuthorityType.HOSTED) {
            log.info("Rejected deletion attempt for {} CA '{}'", certificateAuthority.getType(), caName);
            return ResponseEntity.badRequest().body(RevokeHostedResult.builder()
                    .caName(caName.toString())
                    .revoked(false)
                    .error(String.format("Certificate authority '%s' is not a hosted CA", caName))
                    .build());
        }

        try {
            commandService.execute(new DeleteCertificateAuthorityCommand(
                    certificateAuthority.getVersionedId(),
                    certificateAuthority.getName()
            ));
            log.info("Deleted hosted CA '{}'", caName);

            return ok(RevokeHostedResult.builder()
                    .caName(caName.toString())
                    .revoked(true)
                    .build());
        } catch (Exception e) {
            log.error("Error while deleting hosted CA '{}'", caName, e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(RevokeHostedResult.builder()
                    .caName(caName.toString())
                    .error(e.getMessage())
                    .build());
        }
    }

    @GetMapping
    @Operation(summary = "CA summary")
    public ResponseEntity<ImmutableMap<String, Summary>> summary(@PathVariable("caName") final CaName caName) {
        log.info("Summary for CA: {}", caName);

        Optional<CertificateAuthorityData> certificateAuthority = findCa(CertificateAuthorityData.class, caName);

        boolean wasInstantiated = certificateAuthority.isPresent();
        boolean isHosted = certificateAuthority.filter(ca -> ca.getType() == CertificateAuthorityType.HOSTED).isPresent();

        ImmutableResourceSet certifiableResources = resourceCache.lookupMemberCaPotentialResources(caName.getPrincipal());

        return ok(of("summary", new Summary(
                wasInstantiated,
                !certifiableResources.isEmpty(),
                isHosted,
                Utils.toStringList(certifiableResources)
        )));
    }

    private ProvisioningIdentityCertificate parseCertificate(InputStream uploadedInputStream) throws IOException {
        final String xml = IOUtils.toString(uploadedInputStream, CHARSET_NAME);
        try {
            final ChildIdentity childIdentity = new ChildIdentitySerializer().deserialize(xml);
            return childIdentity.getIdentityCertificate();
        } catch (IdentitySerializer.IdentitySerializerException e) {
            log.info("identity certificate xml rejected by parser: '{}'", xml);
            throw e;
        }
    }

    @GetMapping(path = "issuer-identity")
    @Operation(summary = "CA identity certificate")
    public ResponseEntity<?> identity(@PathVariable("caName") final CaName caName) {
        log.info("Downloading identity certificate for CA: {}", caName);

        final X500Principal principal = caName.getPrincipal();
        final ParentIdentity parentId = delegationCaProvisioningService.getParentIdentityForNonHostedCa(principal);

        if (parentId != null) {
            final String xml = new ParentIdentitySerializer().serialize(parentId);
            final DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMdd");
            final String fileName = "issuer-identity-" + new DateTime().toString(fmt) + ".xml";
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .contentType(TEXT_XML)
                    .header("content-disposition", "attachment; filename = " + fileName)
                    .body(xml.getBytes(CHARSET_NAME));
        } else {
            return ResponseEntity
                    .status(NOT_FOUND) // no body on notFound()
                    .body(bodyForError("Could not find the CA: " + caName));
        }
    }

    @Value
    private static class Summary {
        boolean instantiated;
        boolean hasCertifiableResources;
        boolean hosted;
        List<String> certifiableResources;
    }

}
