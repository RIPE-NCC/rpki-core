package net.ripe.rpki.web;

import lombok.NonNull;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

public abstract class SpringWebControllerTestCase {

    protected MockMvc mockMvc;

    @BeforeEach
    public void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(createSubjectController())
            .setViewResolvers(createThymeleafViewResolver())
            .build();
    }

    @NonNull
    protected abstract Object createSubjectController();

    @NonNull
    protected ThymeleafViewResolver createThymeleafViewResolver() {
        FileTemplateResolver templateResolver = new FileTemplateResolver();
        templateResolver.setPrefix("src/main/resources/WEB-INF/templates/");
        templateResolver.setSuffix(".html");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        return viewResolver;
    }
}
