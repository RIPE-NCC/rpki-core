package net.ripe.rpki.rest.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rest.exception.CaNameInvalidException;
import net.ripe.rpki.rest.exception.CaNotFoundException;
import net.ripe.rpki.rest.exception.UserIdRequiredException;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.security.RunAsUser;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.Formatter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.net.URI;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;
import static net.ripe.rpki.rest.security.SpringAuthInterceptor.USER_ID_REQ_ATTR;

@Slf4j
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class AbstractCaRestService extends RestService {

    @Autowired
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.addCustomFormatter(new CaNameFormatter());
    }

    @PostConstruct
    protected void init() {
        final HttpServletRequest request = getRequest();
        final RunAsUser user = getUserId(request)
                .orElseThrow(() -> new UserIdRequiredException("The cookie '" + USER_ID_HEADER + "' is not defined."));
        request.setAttribute(USER_ID_REQ_ATTR, user);
        RunAsUserHolder.set(user);
    }

    protected <T extends CertificateAuthorityData> Optional<T> findCa(Class<T> type, CaName caName) {
        try {
            return Optional.ofNullable(type.cast(
                certificateAuthorityViewService.findCertificateAuthorityByName(caName.getPrincipal())
            ));
        } catch (ClassCastException ex) {
            throw new CaNotFoundException("certificate authority '" + caName + "' has incorrect type");
        }
    }

    protected <T extends CertificateAuthorityData> T getCa(Class<T> type, CaName caName) {
        return findCa(type, caName).orElseThrow(() -> new CaNotFoundException("certificate authority '" + caName + "' not found"));
    }

    PrefixValidationResult validatePrefix(String prefix, ImmutableResourceSet certifiedResources) {
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

    protected <T> ResponseEntity<T> noContent() {
        return ResponseEntity.noContent().build();
    }

    @NonNull
    protected ResponseEntity<Object> badRequest(String error) {
        return ResponseEntity.badRequest().body(bodyForError(error));
    }

    protected Map<String, String> bodyForError(String error) {
        return Collections.singletonMap("error", error);
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

    private static class CaNameFormatter implements Formatter<CaName> {
        @Override
        public @NonNull CaName parse(@NonNull String text, @NonNull Locale locale) {
            try {
                return CaName.parse(text);
            } catch (CaName.BadCaNameException e) {
                // Will cause a bad request response (see the RestExceptionControllerAdvice)
                throw new CaNameInvalidException(text);
            }
        }

        @Override
        public @NonNull String print(@NonNull CaName object, @NonNull Locale locale) {
            return object.toString();
        }
    }
}
