package net.ripe.rpki.rest.service;

import lombok.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public class RestService {
    public static final String API_URL_PREFIX = "/api/ca";
    public static final String REQUEST_ID_HEADER = "x-request-id";

    @NonNull
    protected static ResponseEntity<Object> badRequestError(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    protected String getRequestId() {
        return getRequest().getHeader(REQUEST_ID_HEADER);
    }

    protected HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
