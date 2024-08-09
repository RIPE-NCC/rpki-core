package net.ripe.rpki.rest.security;

import com.google.common.base.Verify;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

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

    @Order(1)
    @Bean
    public SecurityFilterChain webSecurityFilterChainRequireApiKey(HttpSecurity http, JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint, ApiKeySecurity apiKeySecurity) throws Exception {
        return http
                .securityMatcher(API_REQUEST_MATCHER)
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthenticationEntryPoint))
                .authorizeHttpRequests(r -> r
                        // allow all to /api/monitoring/ endpoints
                        .requestMatchers(new AntPathRequestMatcher("/api/monitoring/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/public/**")).permitAll()
                        // All other paths matching initial .requestMatcher require API key
                        .anyRequest().access(apiKeySecurity)
                )
                .build();
    }


    @Order(2)
    @Bean
    public SecurityFilterChain webSecurityFilterChainAllowProvisioningEndpoint(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PROVISIONING_REQUEST_MATCHER)
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r.anyRequest().permitAll())
                .build();
    }

    @Order(3)
    @Bean
    public SecurityFilterChain webSecurityFilterChainAllowPublicUrls(
        HttpSecurity http,
        @Value("${admin.authorization.enabled:true}") boolean adminAuthEnabled,
        Environment environment
    ) throws Exception {
        http
                .securityMatcher(WEB_REQUEST_MATCHER)
                .authorizeHttpRequests(r -> r
                        .requestMatchers(
                                new AntPathRequestMatcher("/login"),
                                new AntPathRequestMatcher("/actuator/active-node"),
                                new AntPathRequestMatcher("/actuator/prometheus"),
                                new AntPathRequestMatcher("/monitoring/healthcheck")
                        ).permitAll()
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                );

        if (adminAuthEnabled) {
            log.info("enabling OAuth2 and authentication");
            return http
                    .authorizeHttpRequests(r -> r.anyRequest().authenticated())
                    .oauth2Login(withDefaults())
                    .requestCache((cache) -> {
                            var requestCache = new HttpSessionRequestCache();
                            requestCache.setMatchingRequestParameterName(null);
                            cache.requestCache(requestCache);
                    })
                    .build();
        }
        log.warn("NOT enabling authentication, only for use in development mode!");
        // Defense in depth:
        Verify.verify(environment.matchesProfiles("local | test"), "Admin authorization is disabled, but the local or test profile is not active.");

        return http.authorizeHttpRequests(r -> r.anyRequest().permitAll()).build();
    }
}
