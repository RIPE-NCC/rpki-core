package net.ripe.rpki.rest.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class RestService {
    public static final String API_URL_PREFIX = "/api/ca";
    public static final String REQUEST_ID_HEADER = "x-request-id";

    protected String getRequestId() {
        return getRequest().getHeader(REQUEST_ID_HEADER);
    }

    protected HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
