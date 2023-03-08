package net.ripe.rpki.config;

import net.ripe.rpki.rest.security.SpringAuthInterceptor;
import net.ripe.rpki.ripencc.provisioning.ProvisioningMetricsService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@Profile("!test")
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SpringAuthInterceptor()).addPathPatterns("/api/**");
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> factory.setContextPath("/certification");
    }

    @Bean
    public ServletRegistrationBean<ProvisioningServlet> provisioningServlet(
            ProvisioningService provisioningService,
            ProvisioningMetricsService provisioningMetrics) {
        return new ServletRegistrationBean<>(new ProvisioningServlet(provisioningService, provisioningMetrics), "/updown");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/")
                .setViewName("forward:/admin");
    }
}
