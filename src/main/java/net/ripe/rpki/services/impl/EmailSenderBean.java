package net.ripe.rpki.services.impl;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Map;
import java.util.TreeMap;

@Component
@Slf4j
public class EmailSenderBean implements EmailSender {

    private final MailSender mailSender;

    private final SimpleMailMessage templateMessage;

    private final TemplateEngine templateEngine;
    private final Map<String, Object> defaultParameters = new TreeMap<>();

    @Autowired
    public EmailSenderBean(MailSender mailSender, @Value("${mail.template.parameters.rpkiDashboardUri}") String rpkiDashboardUri) {
        this.mailSender = mailSender;
        this.defaultParameters.put("rpkiDashboardUri", rpkiDashboardUri);

        this.templateMessage = new SimpleMailMessage();
        templateMessage.setFrom("noreply@ripe.net");

        templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(textTemplateResolver());

        log.debug("configured email sender with default parameters: {}", defaultParameters);
    }

    @VisibleForTesting
    protected static ITemplateResolver textTemplateResolver() {
        final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setOrder(Integer.valueOf(1));
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(false);
        return templateResolver;
    }

    public void sendEmail(String emailTo, String subject, EmailTemplates template, Map<String, Object> parameters) {
        SimpleMailMessage msg = new SimpleMailMessage(this.templateMessage);
        msg.setSubject(subject);
        msg.setTo(emailTo);

        log.info("Rendering Email template {}", template.templateName);
        msg.setText(renderTemplate(template.templateName, parameters));

        if (!Environment.isLocal()) {
            try {
                log.info("Sending email with subject: {} to: {} ", subject, emailTo);
                this.mailSender.send(msg);
            } catch (MailException e) {
                log.warn("Couldn't send email to '" + emailTo + "'.", e);
            }
        } else {
            log.info("Not sending message in DEVELOPMENT mode:\n" + msg.toString());
        }
    }

    private String renderTemplate(String nameOfTemplate, Map<String, Object> parameters) {
        Context context = new Context();
        context.setVariables(this.defaultParameters);
        context.setVariables(parameters);

        return templateEngine.process(nameOfTemplate, context);
    }

}
