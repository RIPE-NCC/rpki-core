package net.ripe.rpki.services.impl.email;

import java.util.Map;

public interface EmailSender {

    ResultingEmail sendEmail(String emailTo, String subject, EmailTemplates template, Map<String, Object> parameters, String uniqueId);

    // Limit the number of possible inputs to allow us to check all templates in tests.
    enum EmailTemplates {
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY("email-templates/subscribe-confirmation-weekly.txt",
                "Your Resource Certification (RPKI) alerts subscription"),
        ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY("email-templates/subscribe-confirmation-daily.txt",
                "Your Resource Certification (RPKI) alerts subscription"),
        ROA_ALERT_UNSUBSCRIBE("email-templates/unsubscribe-confirmation.txt",
                "Unsubscribe from Resource Certification (RPKI) alerts", false),
        ROA_ALERT("email-templates/roa-alert-email.txt", "Resource Certification (RPKI) alerts for %s"),
        ROA_CHANGE_ALERT("email-templates/roa-change-alert-email.txt", "ROAs changed for %s"),
        ROA_CHANGE_ALERT_SUBSCRIBE_CONFIRMATION("email-templates/subscribe-confirmation-change.txt",
                "Your Resource Certification (RPKI) ROA change alerts subscription"),
        ROA_CHANGE_ALERT_UNSUBSCRIBE_CONFIRMATION("email-templates/unsubscribe-confirmation-change.txt",
                "Your Resource Certification (RPKI) ROA change alerts subscription");

        public final String templateName;
        public final String templateSubject;
        public final boolean generateUnsubscribeUrl;

        EmailTemplates(String templateName, String subject) {
            this(templateName, subject, true);
        }

        EmailTemplates(String templateName, String subject, boolean generateUnsubscribeUrl) {
            this.templateName = templateName;
            this.templateSubject = subject;
            this.generateUnsubscribeUrl = generateUnsubscribeUrl;
        }
    }

    record ResultingEmail(String email, String subject, String body) {}

}
