package net.ripe.rpki.rest.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.exception.PreconditionRequiredException;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.AspaViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/aspa", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/aspa")
@ConditionalOnProperty(prefix="aspa", value="enabled", havingValue = "true")
public class CaAspaConfigurationService extends AbstractCaRestService {
    private final AspaViewService aspaViewService;
    private final CommandService commandService;

    @Autowired
    public CaAspaConfigurationService(
        AspaViewService aspaViewService,
        CommandService commandService
    ) {
        this.aspaViewService = aspaViewService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Get all ASPAs belonging to a CA")
    public ResponseEntity<AspaConfigurationResponse> get(@PathVariable("caName") final CaName caName) {
        log.info("REST call: Get all ASPAs belonging to CA: {}", caName);

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        List<AspaConfigurationData> aspaConfiguration = aspaViewService.findAspaConfiguration(ca.getId());
        String entityTag = AspaConfigurationData.entityTag(AspaConfigurationData.dataToMaps(aspaConfiguration));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .eTag(entityTag)
            .body(AspaConfigurationResponse.of(entityTag, aspaConfiguration));
    }

    @PutMapping
    @Operation(summary = "Update the ASPA configuration for CA")
    public ResponseEntity<Void> put(
        @PathVariable("caName") CaName caName,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatchHeader,
        @RequestBody @Valid AspaConfigurationRequest body
    ) {
        log.info("REST call: Update ASPA configuration belonging to CA: {}", caName);

        String ifMatch = StringUtils.defaultIfEmpty(ifMatchHeader, body.ifMatch);
        if (StringUtils.isBlank(ifMatch)) {
            throw new PreconditionRequiredException("'If-Match' header or 'ifMatch' field required for updating the ASPA configuration");
        }

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        commandService.execute(new UpdateAspaConfigurationCommand(ca.getVersionedId(), ifMatch, body.getAspaConfigurations()));
        return noContent();
    }

    @Value(staticConstructor = "of")
    private static class AspaConfigurationResponse {
        @JsonProperty(required = true)
        @NonNull
        String entityTag;
        @JsonProperty(required = true)
        @NonNull
        List<AspaConfigurationData> aspaConfigurations;
    }

    @Value(staticConstructor = "of")
    private static class AspaConfigurationRequest {
        String ifMatch;
        @NonNull
        @Valid
        List<AspaConfigurationData> aspaConfigurations;
    }
}
