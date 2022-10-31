package net.ripe.rpki.rest.service;

import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;

@ActiveProfiles("test")
@Component
public class Rest {

    public static final String TESTING_API_KEY = "BAD-TEST-D2Shtf2n5Bwh02P7";

    static MockHttpServletRequestBuilder post(String url, String content) {
        return authenticated(withUserId(
            MockMvcRequestBuilders.post(url)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .content(content)
        ));
    }

    static MockHttpServletRequestBuilder post(String url) {
        return authenticated(withUserId(
            MockMvcRequestBuilders.post(url)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
        ));
    }

    static MockHttpServletRequestBuilder put(String url) {
        return authenticated(withUserId(
            MockMvcRequestBuilders.put(url)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
        ));
    }

    public static MockHttpServletRequestBuilder get(String url, Object... vars) {

        return authenticated(withUserId(
            MockMvcRequestBuilders.get(url, vars)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
        ));
    }

    static MockHttpServletRequestBuilder postWithoutApiKey(String url) {
        return withUserId(
            MockMvcRequestBuilders.post(url)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
        );
    }

    static MockHttpServletRequestBuilder delete(String url) {
        return authenticated(withUserId(MockMvcRequestBuilders.delete(url)));
    }

    static MockHttpServletRequestBuilder multipart(String url, String name, byte[] content) {
        return authenticated(withUserId(
            MockMvcRequestBuilders.multipart(url)
                .file(name, content)
                .accept(APPLICATION_JSON)
        ));
    }

    static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder req) {
        return req.header(API_KEY_HEADER, TESTING_API_KEY);
    }

    static MockHttpServletRequestBuilder withUserId(MockHttpServletRequestBuilder req) {
        return req.cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()));
    }
}
