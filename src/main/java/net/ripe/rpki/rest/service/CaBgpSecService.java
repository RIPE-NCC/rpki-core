package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.bgpsec.RouterId;
import net.ripe.rpki.rest.exception.BadRequestException;
import net.ripe.rpki.rest.exception.ObjectNotFoundException;
import net.ripe.rpki.server.api.commands.AddBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.commands.DeleteBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.BgpSecViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/bgpsec", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/bgpsec", description = "Operations on BGPSec certificates")
@ConditionalOnProperty(prefix = "bgpsec", value = "enabled", havingValue = "true")
public class CaBgpSecService extends AbstractCaRestService {

    /**
     * RFC 8551 / RFC 8635 §4: certs-only PKCS#7 MIME type.
     */
    static final String PKCS7_MIME_TYPE = "application/pkcs7-mime; smime-type=certs-only";

    private final BgpSecViewService bgpSecViewService;
    private final CommandService commandService;

    @Autowired
    public CaBgpSecService(BgpSecViewService bgpSecViewService,
                           CommandService commandService,
                           CertificateAuthorityViewService certificateAuthorityViewService) {
        super(certificateAuthorityViewService);
        this.bgpSecViewService = bgpSecViewService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "List all current router keys belonging to the CA",
            description = "Lists all router keys and their CSR, optionally filtered by 'keyIdentifier', 'asn', and/or 'routerId'.")
    public ResponseEntity<RouterKeys> listRouterKeys(
            @PathVariable("caName") final CaName caName,
            @RequestParam(value = "keyIdentifier", required = false) String keyIdentifier,
            @RequestParam(value = "asn", required = false) String asnParam,
            @RequestParam(value = "routerId", required = false) RouterId routerId) {

        log.info("REST call: Get BGPSec objects belonging to CA: {}, key: {}, asn: {}, routerId: {}",
                caName, keyIdentifier, asnParam, routerId);

        var resolvedAsn = parseAsnParam(asnParam);
        var ca = getCa(HostedCertificateAuthorityData.class, caName);
        var allCerts = bgpSecViewService.findBgpSecConfiguration(ca.getId()).stream();
        var filteredCerts = new Filtered<>(allCerts)
                .by(keyIdentifier, BgpSecConfigurationData::keyIdentifier)
                .by(resolvedAsn, BgpSecConfigurationData::asn)
                .by(routerId, BgpSecConfigurationData::routerId)
                .stream()
                .flatMap(conf -> bgpSecViewService.findBgpSecCertificates(ca.getId(), conf.id()).stream());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RouterKeys(filteredCerts.map(RouterKey::from).toList()));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a router key by ID",
        description = "Returns a single router key belonging to the CA by its internal ID."
    )
    public ResponseEntity<RouterKey> getRouterKey(
        @PathVariable("caName") final CaName caName,
        @PathVariable("id") Long id
    ) {
        log.info("REST call: Get BGPSec object {} belonging to CA: {}", id, caName);

        var ca = getCa(HostedCertificateAuthorityData.class, caName);
        var bgpsecConfiguration = bgpSecViewService.findBgpSecCertificates(ca.getId(), id);
        return ResponseEntity.of(bgpsecConfiguration.map(RouterKey::from));
    }

    @GetMapping(path = "/{id}/certificate")
    @Operation(
            summary = "Get the BGPSec router EE certificate as a PKCS#7 certs-only message (RFC 8635 §7)",
            description = "Returns only the BGPSec EE certificate, " +
                    "DER-encoded as a PKCS#7 certs-only message (application/pkcs7-mime; smime-type=certs-only) " +
                    "per RFC 8635 (https://datatracker.ietf.org/doc/html/rfc8635#section-7).")
    public ResponseEntity<byte[]> getCertificatePkcs7(
            @PathVariable("caName") CaName caName,
            @PathVariable("id") Long id
    ) {
        log.info("REST call: Get BGPSec PKCS#7 certificate for Router Key {} belonging to CA: {}", id, caName);
        return buildPkcs7Response(caName, id, bgpSecViewService::findBgpSecCertificatePkcs7, "bgpsec-cert.p7c");
    }

    @GetMapping(path = "{id}/chain")
    @Operation(
            summary = "Get a BGPSec router certificate chain as a PKCS#7 certs-only message (RFC 8635 §7)",
            description = "Returns the BGPSec certificate and the full issuer chain to the RPKI Trust Anchor, " +
                    "DER-encoded as a PKCS#7 certs-only message (application/pkcs7-mime; smime-type=certs-only) " +
                    "per RFC 8635 (https://datatracker.ietf.org/doc/html/rfc8635#section-7).")
    public ResponseEntity<byte[]> getCertificateChainPkcs7(
            @PathVariable("caName") final CaName caName,
            @PathVariable("id") Long id
    ) {
        log.info("REST call: Get BGPSec PKCS#7 certificate chain for Router Key {} belonging to CA: {}", id, caName);
        return buildPkcs7Response(caName, id, bgpSecViewService::findBgpSecCertificateChainPkcs7, "bgpsec-chain.p7c");
    }

    private ResponseEntity<byte[]> buildPkcs7Response(
            CaName caName,
            Long bgpsecConfigurationId,
            BiFunction<Long, BgpSecConfigurationData, Optional<byte[]>> fetchPkcs7,
            String filename) {

        final HostedCertificateAuthorityData ca = getCa(HostedCertificateAuthorityData.class, caName);
        var configuration = bgpSecViewService.findBgpSecCertificates(ca.getId(), bgpsecConfigurationId).orElseThrow(
                () -> new ObjectNotFoundException("BGPSec configuration not found.")
        );

        Optional<byte[]> pkcs7Bytes = fetchPkcs7.apply(ca.getId(), configuration);

        if (pkcs7Bytes.isEmpty()) {
            throw new ObjectNotFoundException(
                    "The BGPSec configuration exists but the certificate has not yet been issued.");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(PKCS7_MIME_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pkcs7Bytes.get());
    }

    @PostMapping
    @Operation(summary = "Submit a BGPSec CSR for the specified CA",
            description = "Submit a single BGPSec certificate signing request (CSR) per RFC 8635 section 7. " +
                    "Returns the registered entry including the certificate chain once issued.")
    public ResponseEntity<RouterKey> submitCsr(
            @PathVariable("caName") final CaName caName,
            @RequestBody CsrRequest body
    ) {
        log.info("REST call: Submit BGPSec CSR belonging to CA: {}", caName);

        if (body.asn() == null) {
            throw new BadRequestException("ASN is required");
        }
        if (body.routerId() == null) {
            throw new BadRequestException("RouterID is required");
        }
        if (StringUtils.isBlank(body.csr())) {
            throw new BadRequestException("CSR is required");
        }

        var ca = getCa(HostedCertificateAuthorityData.class, caName);
        commandService.execute(new AddBgpSecConfigurationCommand(ca.getVersionedId(), body.asn(), body.routerId(), body.csr()));

        var created = bgpSecViewService.findBgpSecConfiguration(ca.getId()).stream()
                .filter(x -> x.matches(body.asn(), body.routerId(), body.csr()))
                .flatMap(bareConf -> {
                    var confWithValidity = bgpSecViewService.findBgpSecCertificates(ca.getId(), bareConf.id());
                    return Stream.of(confWithValidity.orElse(bareConf));
                })
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Failed to find the created BGPSecConfiguration object."));

        return ok(RouterKey.from(created));
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Revoke a specific BGPSec certificate",
            description = "Revoke a single BGPSec certificate by providing its ID.")
    public ResponseEntity<Void> revoke(
            @PathVariable("caName") CaName caName,
            @PathVariable("id") Long id
    ) {
        log.info("REST call: Revoke BGPSec router key {} belonging to CA: {}", id, caName);

        var ca = getCa(HostedCertificateAuthorityData.class, caName);
        bgpSecViewService.findBgpSecCertificates(ca.getId(), id).ifPresent(
                c -> commandService.execute(new DeleteBgpSecConfigurationCommand(
                        ca.getVersionedId(), c))
        );
        return noContent();
    }

    private static Asn parseAsnParam(String asnParam) {
        if (asnParam == null) {
            return null;
        }
        try {
            return Asn.parse(asnParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ASN value: " + asnParam);
        }
    }

    public record CsrRequest(Asn asn, RouterId routerId, String csr) {}

    public record RouterKey(Long routerKeyId, Asn asn, Long routerId, String keyIdentifier, String csr,
                            Instant notValidBefore, Instant notValidAfter) {

        public static RouterKey from(BgpSecConfigurationData data) {
            return new RouterKey(
                    data.id(),
                    data.asn(),
                    data.routerId() != null ? data.routerId().value() : null,
                    data.keyIdentifier(),
                    data.csr(),
                    toInstant(data.notValidBefore()),
                    toInstant(data.notValidAfter())
            );
        }

        private static Instant toInstant(DateTime joda) {
            return joda == null ? null : Instant.ofEpochMilli(joda.getMillis());
        }
    }

    public record RouterKeys(List<RouterKey> routerKeys) {}

    record Filtered<T>(Stream<T> stream) {
        <V> Filtered<T> by(V value, Function<T, V> extract) {
            if (value == null) {
                return this;
            }
            return new Filtered<>(stream.filter(item -> Objects.equals(extract.apply(item), value)));
        }
    }
}
