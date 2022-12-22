package net.ripe.rpki.config;

import lombok.Setter;
import net.ripe.rpki.rest.security.SpringAuthInterceptor;
import net.ripe.rpki.ripencc.provisioning.ProvisioningMetricsService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningServlet;
import net.ripe.rpki.ui.application.CertificationAdminWicketApplication;
import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.protocol.http.servlet.WicketSessionFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;


@Setter
@Configuration
@Profile("!test")
public class WebConfig implements WebMvcConfigurer {
    private static final String WICKET_FILTER = "wicketFilter";

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SpringAuthInterceptor()).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        // Mapped path is used by AdminCertificationBasePage
        registry.addResourceHandler("/portal-theme/**")
                .addResourceLocations("classpath:/portal-theme/")
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));

        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
    }

    @Bean
    public FilterRegistrationBean<WicketFilter> wicketFilter() {
        final FilterRegistrationBean<WicketFilter> wicket = new FilterRegistrationBean<>();
        final WicketFilter filter = new WicketFilter();
        wicket.setFilter(filter);
        wicket.addInitParameter("applicationClassName", CertificationAdminWicketApplication.class.getName());
        wicket.addUrlPatterns("/*");
        wicket.setName(WICKET_FILTER);
        beanFactory.autowireBean(filter);
        return wicket;
    }

    @Bean
    public FilterRegistrationBean<WicketSessionFilter> wicketSessionFilter() {
        final FilterRegistrationBean<WicketSessionFilter> wicketSession = new FilterRegistrationBean<>();
        final WicketSessionFilter filter = new WicketSessionFilter();
        wicketSession.setFilter(filter);
        wicketSession.addInitParameter("filterName", WICKET_FILTER);
        wicketSession.addUrlPatterns("/*");
        wicketSession.setName("wicketSessionFilter");
        beanFactory.autowireBean(filter);
        return wicketSession;
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
}
