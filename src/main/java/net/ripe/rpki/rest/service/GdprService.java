package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.ws.rs.core.MediaType;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = "/api/public/gdpr", produces = MediaType.APPLICATION_JSON)
@Tag(name = "/api/public/gdpr", description = "Return personal data according to GDPR")
public class GdprService {
    private final CommandAuditService commandAuditService;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Autowired
    public GdprService(CommandAuditService commandAuditService,
                       RoaAlertConfigurationRepository roaAlertConfigurationRepository) {
        this.commandAuditService = commandAuditService;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
    }

    @PostMapping("/investigate")
    @Operation(summary = "Search if one or more email addresses are present in RPKI core. Endpoint called by Controlroom.")
    public GdprInvestigationResult investigate(@RequestBody GdprRequest req) {
        var subscriptionEmails = new HashMap<String, List<String>>();
        var reports = new ArrayList<GdprReport>();
        var partOfRegistry = new AtomicBoolean(false);

        req.emails.stream().distinct().forEach(email -> {
            roaAlertConfigurationRepository.findByEmail(email).forEach(rac -> {
                RoaAlertSubscriptionData subscriptionOrNull = rac.getSubscriptionOrNull();
                if (subscriptionOrNull != null) {
                    subscriptionOrNull.getEmails().forEach(email1 ->
                            subscriptionEmails.compute(email1, (e, caNames) -> {
                                if (caNames == null) {
                                    caNames = new ArrayList<>(1);
                                }
                                caNames.add(rac.getCertificateAuthority().getName().getName());
                                return caNames;
                            }));
                }
            });

            var caNames = subscriptionEmails.get(email);
            if (caNames != null) {
                var cas = String.join(", ", caNames);
                reports.add(new GdprReport("Subscription",
                        "Subscribed '" + email + "' for alerts for the CA(s) " + cas, (long) caNames.size()));
            }

            Map<String, Long> mentionsInSummary = commandAuditService.findMentionsInSummary(email);
            if (!mentionsInSummary.isEmpty()) {
                partOfRegistry.set(true);
            }
            mentionsInSummary.forEach((commandType, mentionCount) ->
                    reports.add(new GdprReport(
                            commandType,
                            "'" + email + "' found in the history of commands of type " + commandType,
                            mentionCount)));
        });

        if (req.id != null) {
            Map<String, Long> mentionsInSummary = commandAuditService.findMentionsInSummary(req.id.toString());
            if (!mentionsInSummary.isEmpty()) {
                partOfRegistry.set(true);
            }
            mentionsInSummary.forEach((commandType, mentionCount) ->
                    reports.add(new GdprReport(
                            commandType,
                            "'" + req.id + "' found in the history of commands of type " + commandType,
                            mentionCount)));
        }

        return new GdprInvestigationResult(
                reports,
                reports.stream().anyMatch(r -> r.getOccurrences() > 0),
                partOfRegistry.get());
    }

    @Builder
    @Getter
    public static class GdprReport {
        private final String name;
        private final String description;
        private final Long occurrences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GdprRequest {
        private UUID id;
        private List<String> emails;
    }

    @Builder
    @Getter
    public static class GdprInvestigationResult {
        private List<GdprReport> reports;
        private Boolean anyMatch;
        private Boolean partOfRegistry;
    }
}
