package net.ripe.rpki.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class DbConfig {
    @Bean
    public FlywayConfigurationCustomizer flywayPlaceholders(
            @Value("${online.repository.uri}") String onlineRepositoryUri
    ) {
        Map<String, String> placeholders = Collections.singletonMap(
                "online.repository.uri", onlineRepositoryUri
        );
        return configuration -> configuration.placeholders(placeholders);
    }

    @Bean
    public FlywayMigrationStrategy migrateStrategy() {
        return flyway -> {
            ValidateResult result = flyway.validateWithResult();
            if (!result.validationSuccessful) {
                log.error("Flyway validation failed: {}.\nInvalid migrations are:\n{}\nTrying to repair.",
                        result.errorDetails.errorMessage,
                        result.invalidMigrations.stream()
                                .map(x -> String.format("- %s %s: %s", x.version, x.description, x.errorDetails.errorMessage))
                                .collect(Collectors.joining("\n"))
                );
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
