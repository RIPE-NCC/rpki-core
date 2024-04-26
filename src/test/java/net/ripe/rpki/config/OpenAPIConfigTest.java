package net.ripe.rpki.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.inject.Inject;

import static net.ripe.rpki.RpkiBootApplication.API_KEY_REFERENCE;
import static net.ripe.rpki.RpkiBootApplication.USER_ID_REFERENCE;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;
import static org.junit.Assert.assertEquals;

@Data
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Import(OpenAPIConfig.class)
public class OpenAPIConfigTest {
    /**
     * A bean of OpenAPI type exists because it is defined in OpenAPIConfig.
     */
    @Inject
    private OpenAPI openAPI;

    @Test
    public void testVerifyOpenAPIConfig() {
        SecurityScheme apiKeyScheme = openAPI.getComponents().getSecuritySchemes().get(API_KEY_REFERENCE);
        SecurityScheme userAuditScheme = openAPI.getComponents().getSecuritySchemes().get(USER_ID_REFERENCE);
        Contact contact = openAPI.getInfo().getContact();

        assertEquals(API_KEY_HEADER, apiKeyScheme.getName());
        assertEquals(USER_ID_HEADER, userAuditScheme.getName());
        assertEquals("swe-rpki@ripe.net", contact.getEmail());
    }

}