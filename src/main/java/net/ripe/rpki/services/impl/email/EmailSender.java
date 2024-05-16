package net.ripe.rpki.services.impl.email;

import java.util.Map;

public interface EmailSender {

    void sendEmail(String emailTo, String subject, EmailTemplates template, Map<String, Object> parameters, String uniqueId);

    // Limit the number of possible inputs to allow us to check all templates in tests.
    enum EmailTemplates {
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY("email-templates/subscribe-confirmation-weekly.txt", "Your Resource Certification (RPKI) alerts subscription", true),
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY("email-templates/subscribe-confirmation-daily.txt", "Your Resource Certification (RPKI) alerts subscription", true),
        ROA_ALERT_UNSUBSCRIBE("email-templates/unsubscribe-confirmation.txt", "Unsubscribe from Resource Certification (RPKI) alerts", false),
        ROA_ALERT("email-templates/roa-alert-email.txt", "Resource Certification (RPKI) alerts for %s", true);

        public final String templateName;
        public final String templateSubject;
        public final boolean generateUnsubcribeUrl;

        EmailTemplates(String templateName, String subject, boolean generateUnsubcribeUrl) {
            this.templateName = templateName;
            this.templateSubject = subject;
            this.generateUnsubcribeUrl = generateUnsubcribeUrl;
        }
    }

}
