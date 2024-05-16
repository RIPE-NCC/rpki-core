package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.UnsubscribeFromRoaAlertCommand;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.services.impl.email.EmailTokens;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = "/api/email", produces = MediaType.APPLICATION_JSON)
@Tag(name = "/api/email", description = "Manage email subscriptions")
public class EmailService extends RestService {

    public static final String ERROR = "error";

    private final CommandService commandService;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    private final EmailTokens emailTokens;

    @Autowired
    public EmailService(CommandService commandService,
                        EmailTokens emailTokens,
                        RoaAlertConfigurationRepository roaAlertConfigurationRepository) {
        this.commandService = commandService;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
        this.emailTokens = emailTokens;
    }

    @PostMapping("/unsubscribe/{email}/{token}")
    @Operation(summary = "Implement one-click unsubscribe functionality.")
    public ResponseEntity<?> unsubscribe(
            @PathVariable("email") final String email,
            @PathVariable("token") final String token) {

        if (Strings.isBlank(token)) {
            return ResponseEntity.status(NOT_FOUND).body(Map.of(ERROR, "Unknown or invalid token: " + token));
        }
        var configurations = roaAlertConfigurationRepository.findByEmail(email);
        if (configurations.isEmpty()) {
            return ResponseEntity.status(NOT_FOUND).body(Map.of(ERROR, "Unknown email " + email));
        }
        var unsubscribedAnyone = new AtomicBoolean(false);
        configurations.forEach(configuration -> {
            RoaAlertSubscriptionData subscriptionOrNull = configuration.getSubscriptionOrNull();
            var ca = configuration.getCertificateAuthority();
            var configurationToken = emailTokens.createUnsubscribeToken(EmailTokens.uniqueId(ca.getUuid()), email);
            if (subscriptionOrNull != null
                    && subscriptionOrNull.getEmails().contains(email)
                    && token.equals(configurationToken)) {
                commandService.execute(new UnsubscribeFromRoaAlertCommand(ca.getVersionedId(), email));
                unsubscribedAnyone.set(true);
            }
        });
        if (unsubscribedAnyone.get()) {
            return ResponseEntity.ok().body(Map.of("success", "Unsubscribed " + email));
        }
        return ResponseEntity.status(NOT_FOUND).body(Map.of(ERROR, "Unknown token " + token));
    }
}
