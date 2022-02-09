package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.rest.exception.CaNotFoundException;
import net.ripe.rpki.rest.pojo.ResourcesCollection;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/resources", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/resources", description = "Operations on CA resources")
public class ResourceService extends AbstractCaRestService {
    private final ResourceCertificateViewService resourceCertificateViewService;

    @Autowired
    public ResourceService(ResourceCertificateViewService resourceCertificateViewService) {
        this.resourceCertificateViewService = resourceCertificateViewService;
    }

    @GetMapping
    @Operation(summary = "Get all resources belonging to a CA")
    public ResponseEntity<ResourcesCollection> getResourcesForCa(@PathVariable("caName") final String rawCaName) {
        log.info("Getting resources for CA: {}", rawCaName);

        final IpResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(this.getCaId());
        if (certifiedResources == null) {
            throw new CaNotFoundException(String.format("unknown CA: %s", rawCaName));
        }
        final List<String> resources =
                StreamSupport.stream(certifiedResources.spliterator(), false)
                        .map(Object::toString)
                        .collect(Collectors.toList());
        return ok(new ResourcesCollection(resources));

    }

    @GetMapping(path = "validate-prefix/{prefix}")
    @Operation(summary ="Validate prefix for a CA")
    public ResponseEntity<?> validatePrefix(@PathVariable("caName") final String rawCaName,
                                            @PathVariable("prefix") final String prefix) {
        log.info("Validating prefix[{}] prefix for caName[{}]", prefix, rawCaName);

        final IpResourceSet certifiedResources = resourceCertificateViewService.findCertifiedResources(this.getCaId());
        if (certifiedResources == null) {
            return ResponseEntity.status(NOT_FOUND).body(of("error", "unknown CA: " + rawCaName));
        }

        PrefixValidationResult prefixValidation = validatePrefix(prefix, certifiedResources);

        if (PrefixValidationResult.OK == prefixValidation) {
            return ok(of("status", "valid"));
        } else {
            return ok(of("status", "invalid", "type", prefixValidation.getType(), "message", prefixValidation.getMessage(prefix)));
        }
    }

    /**
     * That should be removed right after proxy configuration is fixed.
     */
    @GetMapping(path = "validate-prefix/{address}/{length}")
    @Operation(summary ="Validate prefix for a CA (this endpoint exists to avoid troubles with '/' encoding)")
    public ResponseEntity<?> validatePrefixByParts(@PathVariable("caName") final String rawCaName,
                                                   @PathVariable("address") final String address,
                                                   @PathVariable("length") final String length) {
        final String prefix = address + "/" + length;
        log.info("Validating prefix[{}] for caName[{}]", prefix, rawCaName);
        return validatePrefix(rawCaName, prefix);
    }
}
