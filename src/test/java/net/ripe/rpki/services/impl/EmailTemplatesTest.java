package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
