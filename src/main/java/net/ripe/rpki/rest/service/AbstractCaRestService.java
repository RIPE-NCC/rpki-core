package net.ripe.rpki.rest.service;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.rest.exception.CaNameInvalidException;
import net.ripe.rpki.rest.exception.CaNotFoundException;
import net.ripe.rpki.rest.exception.UserIdRequiredException;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.security.RunAsUser;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.server.api.support.objects.CaName.BadCaNameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;
import static net.ripe.rpki.rest.security.SpringAuthInterceptor.USER_ID_REQ_ATTR;

@Slf4j
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class AbstractCaRestService {

    static final String API_URL_PREFIX = "/api/ca";

    private static final Pattern CA_NAME_PATTERN = Pattern.compile("^(/certification)?" + API_URL_PREFIX + "/([a-zA-Z0-9-]+)");

    private String rawCaName;
    private CertificateAuthorityData ca;
    private final boolean exclusiveForHosted;
    private final boolean verifyCaExists;

    public AbstractCaRestService() {
        exclusiveForHosted = true;
        verifyCaExists = true;
    }

    public AbstractCaRestService(Boolean exclusiveForHosted, Boolean verifyCaExists) {
        this.exclusiveForHosted = exclusiveForHosted;
        this.verifyCaExists = verifyCaExists;
    }

    @Autowired
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @PostConstruct
    protected void init() {

        int contextLength = getRequest().getContextPath().length();
        String apiRelativePath = getRequest().getRequestURI().substring(contextLength);
        if(!apiRelativePath.startsWith(API_URL_PREFIX)) {
            return;
        }
        rawCaName = getRawCaNameFromRequest();
        try {
            ca = getCaByName(rawCaName);
        } catch (BadCaNameException e) {
            throw new CaNameInvalidException(rawCaName);
        }

        if (verifyCaExists && ca == null) {
            throw new CaNotFoundException(String.format("unknown CA: %s", rawCaName));
        }

        if (exclusiveForHosted && CertificateAuthorityType.NONHOSTED == ca.getType()) {
            throw new CaNotFoundException(String.format("operation not available for non-hosted CA: %s", rawCaName));
        }

        final HttpServletRequest request = getRequest();
        final RunAsUser user = getUserId(request)
                .orElseThrow(() -> new UserIdRequiredException("The cookie '" + USER_ID_HEADER + "' is not defined."));
        request.setAttribute(USER_ID_REQ_ATTR, user);
        RunAsUserHolder.set(user);
    }

    protected String getRawCaName() {
        return rawCaName;
    }

    protected long getCaId() {
        return ca.getId();
    }

    protected VersionedId getVersionedId() {
        return ca.getVersionedId();
    }

    private String getRawCaNameFromRequest() {
        String path = getRequest().getRequestURI();
        Matcher matcher = CA_NAME_PATTERN.matcher(path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Request doesn't start with [/api/ca/{caName}] found [" + path + "]");
        }
        return matcher.group(2);
    }

    private CertificateAuthorityData getCaByName(String unparsedCaName) {
        CaName caName = CaName.parse(unparsedCaName);
        return certificateAuthorityViewService.findCertificateAuthorityByName(caName.getPrincipal());
    }

    protected HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    PrefixValidationResult validatePrefix(String prefix, IpResourceSet certifiedResources) {
        final IpRange range;
        try {
            range = IpRange.parse(prefix);
            if (!range.isLegalPrefix()) {
                return PrefixValidationResult.SYNTAX_ERROR;
            }
        } catch (Exception e) {
            return PrefixValidationResult.SYNTAX_ERROR;
        }
        if (!certifiedResources.contains(range)) {
            return PrefixValidationResult.OWNERSHIP_ERROR;
        }
        return PrefixValidationResult.OK;
    }

    private Optional<RunAsUser> getUserId(final HttpServletRequest request) {
        return Optional.ofNullable(request.getCookies())
                .flatMap(
                        cookies -> Arrays.stream(cookies)
                                .filter(x -> USER_ID_HEADER.equals(x.getName()))
                                .findFirst()
                )
                .flatMap(cookie -> {
                    try {
                        return Optional.of(UUID.fromString(cookie.getValue()));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                }).map(RunAsUser::operator);
    }

    protected <T> ResponseEntity<T> ok(T t) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(t);
    }

    protected <T> ResponseEntity<T> ok() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    protected <T> ResponseEntity<T> created() {
        return ResponseEntity
                .created(URI.create(getRequest().getRequestURI()))
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    enum PrefixValidationResult {
        SYNTAX_ERROR("syntax", "%s is not a legal prefix"),
        OWNERSHIP_ERROR("ownership", "You are not a holder of the prefix %s"),
        OK("ok", "");

        private final String type;
        private final String message;


        PrefixValidationResult(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public String getMessage(String resource) {
            return String.format(message, resource);
        }

        public String getType() {
            return type;
        }
    }
}
