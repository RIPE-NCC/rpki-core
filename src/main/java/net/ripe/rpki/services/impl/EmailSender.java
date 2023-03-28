package net.ripe.rpki.services.impl;

import java.util.Map;

public interface EmailSender {

    void sendEmail(String emailTo, String subject, EmailTemplates template, Map<String, Object> parameters);

    // Limit the number of possible inputs to allow us to check all templates in tests.
    enum EmailTemplates {
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY("email-templates/subscribe-confirmation-weekly.txt", "Your Resource Certification (RPKI) alerts subscription"),
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY("email-templates/subscribe-confirmation-daily.txt", "Your Resource Certification (RPKI) alerts subscription"),
        ROA_ALERT_UNSUBSCRIBE("email-templates/unsubscribe-confirmation.txt", "Unsubscribe from Resource Certification (RPKI) alerts"),
        ROA_ALERT("email-templates/roa-alert-email.txt", "Resource Certification (RPKI) alerts for %s");

        public final String templateName;
        public final String templateSubject;

        private EmailTemplates(String templateName, String subject) {
            this.templateName = templateName;
            this.templateSubject = subject;
        }


    }
}
