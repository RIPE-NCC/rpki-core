package net.ripe.rpki.rest.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@ComponentScan
@Slf4j
public class SecurityConfig {

    private static final RequestMatcher API_REQUEST_MATCHER = new OrRequestMatcher(
        new AntPathRequestMatcher("/api/**"),
        new AntPathRequestMatcher("/prod/ca/**")
    );
    private static final RequestMatcher PROVISIONING_REQUEST_MATCHER = new AntPathRequestMatcher("/updown");
    private static final RequestMatcher WEB_REQUEST_MATCHER =
        new NegatedRequestMatcher(new OrRequestMatcher(API_REQUEST_MATCHER, PROVISIONING_REQUEST_MATCHER));

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint) throws Exception {
        return http
            .requestMatcher(API_REQUEST_MATCHER)
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthenticationEntryPoint))
            .authorizeRequests(r -> r
                // allow all to /api/monitoring/ endpoints
                .antMatchers("/api/monitoring/**").permitAll()
                .antMatchers("/api/public/**").permitAll()
                // All other paths matching initial .requestMatcher require API key
                .anyRequest().access("@apiKeySecurity.check(request)")
            )
            .build();
    }

    @Bean
    public SecurityFilterChain provisioningSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .requestMatcher(PROVISIONING_REQUEST_MATCHER)
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeRequests(r -> r.anyRequest().permitAll())
            .build();
    }

    @Bean
    public SecurityFilterChain webSecurityFilterChain(
        HttpSecurity http,
        @Value("${authorization.admin.role}") String adminRole
    ) throws Exception {
        http.requestMatcher(WEB_REQUEST_MATCHER)
            .authorizeRequests(r -> r
                .antMatchers(
                    "/login",
                    "/actuator/active-node/",
                    "/actuator/prometheus",
                    "/monitoring/healthcheck"
                    ).permitAll()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .anyRequest().hasAuthority(adminRole)
            );

        if ("ROLE_ANONYMOUS".equals(adminRole)) {
            log.warn("NOT enabling OAuth2 security, only for use in development mode!");
        } else {
            log.info("enabling OAuth2 security using administrator role {}", adminRole);
            http.oauth2Login();
        }

        return http.build();
    }
}
