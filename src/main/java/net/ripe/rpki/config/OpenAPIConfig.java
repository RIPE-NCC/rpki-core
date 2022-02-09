package net.ripe.rpki.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static net.ripe.rpki.RpkiBootApplication.API_KEY_REFERENCE;
import static net.ripe.rpki.RpkiBootApplication.USER_ID_REFERENCE;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;

@Configuration
public class OpenAPIConfig {
    @Bean(name="certificationOpenApi")
    public OpenAPI certificationAPI() {
        return new OpenAPI().info(getInfo())
                .addSecurityItem(new SecurityRequirement()
                        .addList(API_KEY_REFERENCE)
                        .addList(USER_ID_REFERENCE))
                .components(getSecuritySchemes());
    }

    private Components getSecuritySchemes() {
        return new Components()
                .addSecuritySchemes(API_KEY_REFERENCE,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .name(API_KEY_HEADER)
                                .description(API_KEY_REFERENCE)
                                .in(SecurityScheme.In.HEADER))
                .addSecuritySchemes(USER_ID_REFERENCE,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .name(USER_ID_HEADER)
                                .description(USER_ID_REFERENCE)
                                .in(SecurityScheme.In.COOKIE));
    }

    private Info getInfo() {

        return new Info().title("Resource Certification (RPKI) API")
                .termsOfService("https://www.ripe.net/lir-services/resource-management/certification/legal/ripe-ncc-certification-service-terms-and-conditions")
                .description("Rest API for RIPE NCC Resource Certification (RPKI)")
                .contact(new Contact().email("swe-rpki@ripe.net"))
                .version("v1.2");
    }
}
