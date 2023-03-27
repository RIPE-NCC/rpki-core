package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.io.StringWriter;
import java.util.Map;

@Component
@Slf4j
public class EmailSenderBean implements EmailSender {

    private final MailSender mailSender;

    private final SimpleMailMessage templateMessage;

    private final TemplateEngine templateEngine;

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderBean.class);

    @Autowired
    public EmailSenderBean(MailSender mailSender) {
        this.mailSender = mailSender;

        this.templateMessage = new SimpleMailMessage();
        templateMessage.setFrom("noreply@ripe.net");

        templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(templateResolver());
    }

    private ITemplateResolver templateResolver() {
        final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setOrder(Integer.valueOf(1));
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(false);
        return templateResolver;
    }

    public void sendEmail(String emailTo, String subject, String nameOfTemplate, Map<String, Object> parameters) {
        SimpleMailMessage msg = new SimpleMailMessage(this.templateMessage);
        msg.setSubject(subject);
        msg.setTo(emailTo);

        LOG.info("Rendering Email template {}", nameOfTemplate);
        msg.setText(renderTemplate(nameOfTemplate, parameters));

        if (!Environment.isLocal()) {
            try {
                LOG.info("Sending email with subject: {} to: {} ", subject, emailTo);
                this.mailSender.send(msg);
            } catch (MailException e) {
                LOG.warn("Couldn't send email to '" + emailTo + "'.", e);
            }
        } else {
            LOG.info("Not sending message in DEVELOPMENT mode:\n" + msg.toString());
        }
    }

    private String renderTemplate(String nameOfTemplate, Map<String, Object> parameters) {

        Context context = new Context();
        parameters.forEach(context::setVariable);

        final StringWriter sw = new StringWriter();
        templateEngine.process(nameOfTemplate, context, sw);
        return sw.toString();
    }

}
