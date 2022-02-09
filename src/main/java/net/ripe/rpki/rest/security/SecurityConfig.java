package net.ripe.rpki.rest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
@ComponentScan
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Autowired
    public SecurityConfig(RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //If there are multiple matchers, the order is important. First match wins!
        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().exceptionHandling().authenticationEntryPoint(restAuthenticationEntryPoint)
                // If there are multiple matchers the order is important. First match wins.
                .and().authorizeRequests()
                    // allow all to /api/monitoring/ endpoints
                    .antMatchers("/api/monitoring/**").permitAll()
                    .antMatchers("/api/public/**").permitAll()
                    // check API headers on other requests.
                    .antMatchers("/api/**").access("@apiKeySecurity.check(request)");
    }

    /**
     * @see <a href="https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configuration/WebSecurityConfigurerAdapter.html#configure(org.springframework.security.config.annotation.web.builders.WebSecurity)">spring security API documentation</a>
     * ..Override this method to configure WebSecurity. For example, if you wish to ignore certain requests. Endpoints specified in this method will be ignored by Spring Security...
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/certification/","/monitoring/**");
        web.ignoring().antMatchers(
                "/swagger-ui/index.html",
                "/v3/api-docs/**",
                "/monitoring/**");

    }
}
