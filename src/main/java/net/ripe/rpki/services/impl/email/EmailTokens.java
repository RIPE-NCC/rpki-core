package net.ripe.rpki.services.impl.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EmailTokens {

    private final String authUnsubscribeUri;
    private final String apiUnsubscribeUri;
    private final Mac mac;

    public EmailTokens(@Value("${mail.unsubscribe.secret}") String unsubscribeSecret,
                       @Value("${mail.template.parameters.authUnsubscribeUri}") String authUnsubscribeUri,
                       @Value("${mail.template.parameters.apiUnsubscribeUri}") String apiUnsubscribeUri) {
        this.authUnsubscribeUri = authUnsubscribeUri;
        this.apiUnsubscribeUri = apiUnsubscribeUri;
        this.mac = initMac(unsubscribeSecret);
    }

    /**
     * This is to generate some unique identifier corresponding to a CA to mix
     * into the unsubscribe tokens.
     */
    public static String uniqueId(Object o) {
        return String.valueOf(o);
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * A token used for an unsubscribe link is a hmac of a server-wide secret,
     * unique id corresponding to the CA and the user's email.
     */
    public String createUnsubscribeToken(String uniqueId, String email) {
        var bytes = mac.doFinal((uniqueId + email).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    private static Mac initMac(String unsubscribeSecret) {
        try {
            var algorithm = "HmacSHA256";
            var mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(secretKeySpec);
            return mac;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String makeUnsubscribeUrl(String uniqueId, String email) {
        var unsubscribeToken = createUnsubscribeToken(uniqueId, email);
        var apiUrl = apiUnsubscribeUri + "/" + enc(email) + "/" + unsubscribeToken;
        return authUnsubscribeUri + enc(apiUrl);
    }
}
