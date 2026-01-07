package net.ripe.rpki.rest.security;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.application.CertificationConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

@Slf4j
@Component
public class ApiKeySecurity implements AuthorizationManager<RequestAuthorizationContext> {
    public static final String API_KEY_HEADER = "ncc-internal-api-key";
    public static final String USER_ID_HEADER = "user-id";

    private final Properties apiKeys = new Properties();

    @Autowired
    public ApiKeySecurity(CertificationConfiguration certificationConfiguration) {
        try (InputStream in = certificationConfiguration.getApiKeys().getInputStream()) {
            apiKeys.load(in);
        } catch (IOException ioe) {
            log.error(
                "Unable to load api-keys.properties from {}: {}. Bailing out.",
                certificationConfiguration.getApiKeys().toString(),
                ioe.getMessage()
            );
            System.exit(1);
        }
    }

    @Override
    @Deprecated(forRemoval = true)
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext ctx) {
        return authorize(authentication, ctx);
    }

    @Override
    public AuthorizationDecision authorize(Supplier<Authentication> authentication, RequestAuthorizationContext ctx) {
        final String apikey = ctx.getRequest().getHeader(API_KEY_HEADER);
        var granted = apikey != null && apiKeys.containsKey(apikey);
        return new AuthorizationDecision(granted);
    }
}
