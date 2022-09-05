package net.ripe.rpki.rest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ComponentScan
public class SecurityConfig {

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Autowired
    public SecurityConfig(RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
        return http.build();
    }
}
