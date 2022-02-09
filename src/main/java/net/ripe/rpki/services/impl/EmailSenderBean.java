package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

@Component
@Slf4j
public class EmailSenderBean implements EmailSender {

    private final MailSender mailSender;

    private final SimpleMailMessage templateMessage;

    private final VelocityEngine velocityEngine;

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderBean.class);

    @Autowired
    public EmailSenderBean(MailSender mailSender) {
        this.mailSender = mailSender;

        this.templateMessage = new SimpleMailMessage();
        templateMessage.setFrom("noreply@ripe.net");

        Properties p = new Properties();
        p.put("resource.loader", "class");
        p.put("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine = new VelocityEngine(p);
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
        final VelocityContext velocityContext = new VelocityContext();
        parameters.forEach(velocityContext::put);
        final StringWriter sw = new StringWriter();
        velocityEngine.mergeTemplate(nameOfTemplate, "UTF-8", velocityContext, sw);
        return sw.toString();
    }

}
