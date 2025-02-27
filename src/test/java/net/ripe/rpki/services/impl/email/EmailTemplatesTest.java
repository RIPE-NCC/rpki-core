package net.ripe.rpki.services.impl.email;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.services.impl.email.EmailSenderBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;

@Slf4j
public class EmailTemplatesTest {
    private TemplateEngine templateEngine;

    @BeforeEach
    public void setUp() {
        templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(EmailSenderBean.textTemplateResolver());
    }

    @Test
    public void testTemplateForSyntacticValdidity() {
        // Add required variables (i.e. those that are dereferenced) here
        var ctx = new Context();

        // Process all the supported templates
        Arrays.stream(EmailSender.EmailTemplates.values()).forEach(template -> {
            log.info("Processing " + template.templateName);
            log.debug(templateEngine.process(template.templateName, ctx));
        });
    }
}
