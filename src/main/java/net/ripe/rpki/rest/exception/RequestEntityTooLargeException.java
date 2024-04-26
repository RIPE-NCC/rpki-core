package net.ripe.rpki.rest.exception;

import net.ripe.rpki.rest.security.RequestEntitySizeLimiterServletFilter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Thrown by the {@link RequestEntitySizeLimiterServletFilter request body size limiter filter} when the
 * {@link jakarta.servlet.ServletInputStream input stream} of the {@link jakarta.servlet.http.HttpServletRequest request}
 * is too large.
 *
 * Translated by {@link RestExceptionControllerAdvice#exceptionsResultingInRequestEntityTooLargeHandler(HttpServletRequest, RequestEntityTooLargeException)
 * controller advice exception handler} for the REST API and by the {@link net.ripe.rpki.ripencc.provisioning.ProvisioningServlet provisioning servlet}
 * for the up/down protocol.
 *
 * Extends {@link RuntimeException RuntimeException} since {@link java.io.InputStream#read(byte[], int, int)} catches
 * and drops {@link IOException IOException}.
 */
public class RequestEntityTooLargeException extends RuntimeException {
    public RequestEntityTooLargeException() {
        super("request entity too large");
    }
}
