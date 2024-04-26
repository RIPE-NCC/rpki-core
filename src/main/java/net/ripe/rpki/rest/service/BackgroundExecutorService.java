package net.ripe.rpki.rest.service;

import com.google.common.base.Strings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

@Slf4j
@Scope("prototype")
@ConditionalOnProperty(prefix="system.setup.and.testing.api", value="enabled", havingValue = "true")
@RestController
@RequestMapping(path = "/api/background/service/{serviceName}", produces = { APPLICATION_JSON })
@Tag(name = "/api/background/service/{serviceName}", description = "Rest Endpoint for background service")
public class BackgroundExecutorService extends RestService {

    private final BackgroundServices backgroundServices;

    @Autowired
    public BackgroundExecutorService(BackgroundServices backgroundServices) {
        this.backgroundServices = backgroundServices;
    }

    @GetMapping
    @Operation(summary = "Provides status of Service - 'running', 'blocked since ...', or 'not running'.")
    public ResponseEntity<String> getBackgroundServiceStatus(@PathVariable("serviceName") String service) {
        log.info("Background service status requested for {}", service);
        try {
            if (isNotValid(service)) {
                return logAndReturnResponse(BAD_REQUEST, "service missing or invalid - " + service);
            }
            BackgroundService backgroundService = backgroundServices.getByName(service);
            return logAndReturnResponse(OK, service + " is " + backgroundService.getStatus());
        } catch (Exception e) {
            return logAndReturnResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Schedules the background service for execution")
    public ResponseEntity<String> executeInBackground(@PathVariable("serviceName") String serviceName, @RequestParam Map<String, String> parameters) {
        log.info("Background service execution requested for {}", serviceName);
        try {
            if (isNotValid(serviceName)) {
                return logAndReturnResponse(BAD_REQUEST, "service missing or invalid - " + serviceName);
            }

            if (parameters.size() > 10) {
                return logAndReturnResponse(BAD_REQUEST, "too many job parameters");
            }

            final BackgroundService backgroundService = backgroundServices.getByName(serviceName);
            Set<String> supportedParameters = backgroundService.supportedParameters().keySet();

            // Restrict parameter names to known values and parameter values to short, simple strings to avoid
            // potential injection attacks.
            List<Map.Entry<String, String>> badParameters = parameters.entrySet().stream()
                    .filter(entry ->
                            !supportedParameters.contains(entry.getKey())
                                    || entry.getValue().length() > 100
                                    || !entry.getValue().matches("[0-9a-zA-Z_:-]*")
                    ).toList();
            if (!badParameters.isEmpty()) {
                return logAndReturnResponse(BAD_REQUEST, "incorrect job parameter(s) - " + badParameters);
            }

            if (backgroundService.isWaitingOrRunning()) {
                return logAndReturnResponse(PRECONDITION_FAILED, serviceName + " is already waiting or running");
            }
            backgroundServices.trigger(serviceName, parameters);
            return logAndReturnResponse(OK, backgroundService.getName() + " has been triggered through REST API");
        } catch (Exception e) {
            return logAndReturnResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private boolean isNotValid(String serviceName) {
        return Strings.isNullOrEmpty(serviceName) || backgroundServices.getByName(serviceName) == null;
    }

    private ResponseEntity<String> logAndReturnResponse(HttpStatus status, String message) {
        log.info(message);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(message);
    }
}
