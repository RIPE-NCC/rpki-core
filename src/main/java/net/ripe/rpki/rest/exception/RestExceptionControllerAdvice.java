package net.ripe.rpki.rest.exception;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Rewrite exceptions to JSON responses.
 * Used over ResponseStatusException because that returns the json+hal Content-Type which
 * consumers may not expect.
 */
@ControllerAdvice
public class RestExceptionControllerAdvice {
    private ResponseEntity<Map<String, ?>> exceptionBody(Throwable e, HttpStatus status, String path) {
        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ImmutableMap.of(
                        "timestamp", LocalDateTime.now(),
                        "status", status.value(),
                        "error", e.getMessage(),
                        "message", e.getLocalizedMessage(),
                        "path", path
                        )
                );
    }

    @ExceptionHandler({
            UserIdRequiredException.class
    })
    public ResponseEntity<Map<String, ?>> exceptionsResultingInForbiddenHandler(HttpServletRequest req, UserIdRequiredException e) {
        return exceptionBody(e, HttpStatus.FORBIDDEN,req.getServletPath());
    }

    @ExceptionHandler({
            BadRequestException.class,
            CaNameInvalidException.class
    })
    public ResponseEntity<Map<String, ?>> exceptionsResultingInBadRequestHandler(HttpServletRequest req, Exception e) {
        // For some reason Spring passes in the main exception instead of the cause exception that matches the @ExceptionHandler annotation :(
        Throwable cause = ExceptionUtils.getRootCause(e);
        return exceptionBody(cause != null ? cause : e, HttpStatus.BAD_REQUEST, req.getServletPath());
    }

    @ExceptionHandler({
            CaNotFoundException.class,
            ObjectNotFoundException.class
    })
    public ResponseEntity<Map<String, ?>> exceptionsResultingInNotFoundHandler(HttpServletRequest req, Exception e) {
        // For some reason Spring passes in the main exception instead of the cause exception that matches the @ExceptionHandler annotation :(
        Throwable cause = ExceptionUtils.getRootCause(e);
        return exceptionBody(cause != null ? cause : e, HttpStatus.NOT_FOUND, req.getServletPath());
    }

    @ExceptionHandler(value = RequestEntityTooLargeException.class)
    public ResponseEntity<Map<String, ?>> exceptionsResultingInRequestEntityTooLargeHandler(HttpServletRequest req, RequestEntityTooLargeException e) {
        return exceptionBody(e, HttpStatus.PAYLOAD_TOO_LARGE, req.getServletPath());
    }
}
