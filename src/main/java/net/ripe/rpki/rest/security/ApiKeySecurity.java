package net.ripe.rpki.rest.security;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.application.CertificationConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Properties;

@Slf4j
@Component
public class ApiKeySecurity {
    public static final String API_KEY_HEADER = "ncc-internal-api-key";
    public static final String USER_ID_HEADER = "user-id";

    private Properties apiKeys = new Properties();

    @Autowired
    public ApiKeySecurity(CertificationConfiguration certificationConfiguration) {
        try {
            apiKeys.load(certificationConfiguration.getApiKeys().getInputStream());
        } catch (IOException ioe) {
            log.error("Unable to load api-keys.properties from {} bailing out", certificationConfiguration.getApiKeys().toString());
            System.exit(1);
        }
    }

    public boolean check(HttpServletRequest request) {
        final String apikey = request.getHeader(API_KEY_HEADER);
        return apikey != null && apiKeys.containsKey(apikey);
    }
}
