package net.ripe.rpki.services.impl.email;

import com.google.common.annotations.VisibleForTesting;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
@Slf4j
public class EmailSenderBean implements EmailSender {

    public static final String NO_REPLY_RIPE_NET = "noreply@ripe.net";
    public static final String RPKI_RIPE_NET = "rpki@ripe.net";

    private final MailSender mailSender;
    private final SimpleMailMessage templateMessage;

    private final TemplateEngine templateEngine;
    private final Map<String, Object> defaultParameters = new TreeMap<>();
    private final EmailTokens emailTokens;

    @Autowired
    public EmailSenderBean(MailSender mailSender, EmailTokens emailTokens,
                           @Value("${mail.template.parameters.rpkiDashboardUri}") String rpkiDashboardUri) {
        this.mailSender = mailSender;
        this.defaultParameters.put("rpkiDashboardUri", rpkiDashboardUri);
        this.emailTokens = emailTokens;

        this.templateMessage = new SimpleMailMessage();
        templateMessage.setFrom(NO_REPLY_RIPE_NET);

        templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(textTemplateResolver());

        log.debug("configured email sender with default parameters: {}", defaultParameters);
    }

    @VisibleForTesting
    protected static ITemplateResolver textTemplateResolver() {
        final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setOrder(1);
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(false);
        return templateResolver;
    }

    @Override
    public ResultingEmail sendEmail(String emailTo, String subject, EmailTemplates template, Map<String, Object> parameters, String uniqueId) {
        if (!(mailSender instanceof JavaMailSenderImpl)) {
            log.error("mailSender is not configured properly, {}", mailSender.getClass());
            return null;
        }

        try {
            var javaMailSender = (JavaMailSenderImpl) mailSender;
            var noReply = new InternetAddress(NO_REPLY_RIPE_NET);
            MimeMessage message = javaMailSender.createMimeMessage();
            message.setFrom(noReply);
            message.setReplyTo(new InternetAddress[]{noReply});
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(emailTo));
            message.setSubject(subject);
            var parametersUpdated = new HashMap<>(parameters);
            parametersUpdated.put("rpkiEmail", RPKI_RIPE_NET);
            if (template.generateUnsubscribeUrl) {
                var unsubscribeUri = emailTokens.makeUnsubscribeUrl(uniqueId, emailTo);
                message.addHeader("List-Unsubscribe", "<" + unsubscribeUri + ">");
                message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                parametersUpdated.put("unsubscribeUri", unsubscribeUri);
            }

            log.info("Rendering Email template {}", template.templateName);
            var body = renderTemplate(template.templateName, parametersUpdated);
            message.setText(body);

            if (!Environment.isLocal()) {
                try {
                    log.info("Sending email with subject: {} to: {} ", subject, emailTo);
                    javaMailSender.send(message);
                } catch (MailException e) {
                    log.warn("Couldn't send email to '" + emailTo + "'.", e);
                }
            } else {
                log.info("Not sending message in DEVELOPMENT mode:\n" + message);
            }
            return new ResultingEmail(emailTo, subject, body);
        } catch (Exception e) {
            log.error("Failed to send email", e);
            return null;
        }
    }

    private String renderTemplate(String nameOfTemplate, Map<String, Object> parameters) {
        Context context = new Context();
        context.setVariables(this.defaultParameters);
        context.setVariables(parameters);
        return templateEngine.process(nameOfTemplate, context);
    }

}
