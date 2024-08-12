package net.ripe.rpki.rest.service.monitoring;

import com.google.common.hash.Hashing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.ripe.rpki.commons.validation.roa.RoaPrefixData;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.WebRequest;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Tag(name = "/api/monitoring/roa-prefixes")
@Controller
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RoaPrefixesService {
    @Autowired
    private RoaConfigurationRepository roaConfigurationRepository;

    @Operation(summary = "Get all the ROA prefixes")
    @GetMapping("/api/monitoring/roa-prefixes")
    public ResponseEntity<ValidatedObjectsResponse<RoaConfigurationPrefixData>> listRoaPrefixes(WebRequest request) {
        // Track content changes by tracking the count of ROA prefixes + last modified of the roa configurations.
        final boolean returnNotModified = roaConfigurationRepository.lastModified().map(lastModified -> {
            final String hash = Hashing.sha256()
                    .newHasher()
                    .putLong(roaConfigurationRepository.countRoaPrefixes())
                    .putLong(lastModified.toEpochMilli())
                    .hash()
                    .toString();
            return request.checkNotModified(hash);
        }).orElse(false);

        if (returnNotModified) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        List<RoaConfigurationPrefixData> roas = roaConfigurationRepository.findAllPrefixes().stream()
                .sorted(RoaPrefixData.ROA_PREFIX_DATA_COMPARATOR)
                .toList();

        return ResponseEntity.ok(ValidatedObjectsResponse.of(roas, Collections.singletonMap("origin", "rpki-core")));
    }

    @Value(staticConstructor = "of")
    private static class ValidatedObjectsResponse<T> {
        private List<T> roas;
        private Map<String, String> metadata;
    }
}
