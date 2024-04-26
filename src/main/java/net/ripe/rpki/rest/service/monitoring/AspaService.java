package net.ripe.rpki.rest.service.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Tag(name = "/api/monitoring/aspa")
@Controller
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AspaService {
    @Autowired
    private final AspaConfigurationRepository aspaConfigurationRepository;

    @Operation(summary = "Get all the ASPA configurations")
    @GetMapping("/api/monitoring/aspa-configurations")
    public ResponseEntity<ValidatedObjectsResponse<AspaConfigurationData>> listAspaConfigs() {
        final Collection<AspaConfiguration> aspaConfigurations = aspaConfigurationRepository.findAll();
        final List<AspaConfigurationData> aspas = aspaConfigurations.stream()
                .map(AspaConfiguration::toData).toList();
        return ResponseEntity.ok(ValidatedObjectsResponse.of(aspas, Collections.singletonMap("origin", "rpki-core")));
    }

    @Value(staticConstructor = "of")
    private static class ValidatedObjectsResponse<T> {
        private List<T> aspaConfigurations;
        private Map<String, String> metadata;
    }
}
