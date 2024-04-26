package net.ripe.rpki.rest.service;

import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.rest.pojo.ResourcesCollection;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.google.common.collect.ImmutableMap.of;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/resources", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/resources", description = "Operations on CA resources")
public class ResourceService extends AbstractCaRestService {

    @GetMapping
    @Operation(summary = "Get all **certified** resources belonging to a CA")
    public ResponseEntity<ResourcesCollection> getResourcesForCa(@PathVariable("caName") final CaName caName) {
        log.info("Getting resources for CA: {}", caName);

        final CertificateAuthorityData ca = getCa(CertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();
        return ok(new ResourcesCollection(Utils.toStringList(certifiedResources)));
    }

    @GetMapping(path = "validate-prefix/{prefix}")
    @Operation(summary ="Validate prefix for a CA")
    public ResponseEntity<ImmutableMap<String, String>> validatePrefix(@PathVariable("caName") final CaName caName,
                                                                       @PathVariable("prefix") final String prefix) {
        log.info("Validating prefix[{}] prefix for caName[{}]", prefix, caName);

        final CertificateAuthorityData ca = getCa(CertificateAuthorityData.class, caName);
        final ImmutableResourceSet certifiedResources = ca.getResources();

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
    public ResponseEntity<ImmutableMap<String, String>> validatePrefixByParts(@PathVariable("caName") final CaName caName,
                                                   @PathVariable("address") final String address,
                                                   @PathVariable("length") final String length) {
        final String prefix = address + "/" + length;
        return validatePrefix(caName, prefix);
    }
}
