package net.ripe.rpki.rest.service;

import lombok.AllArgsConstructor;
import net.ripe.rpki.services.impl.background.ResourceCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * <pre>
 * curl -i -X PATCH  \
 *         -H 'ncc-internal-api-key: BAD-TEST-[test-api-key]' \
 *         "https://[host]/certification/api/resource-cache?updateVerificationCode=[code]"
 * </pre>
 */
@AllArgsConstructor
@RequestMapping("/api/resource-cache")
@ConditionalOnProperty(prefix="system.setup.and.testing.api", value="enabled", havingValue = "true")
@RestController
public class ResourceCacheUpdateController {
    private final ResourceCacheService resourceCacheService;

    @GetMapping
    public ResourceCacheService.ResourceStat getStatus() {
        return resourceCacheService.getResourceStats();
    }

    @PatchMapping
    public ResponseEntity<Map<String, Boolean>> update(
        @RequestParam("updateVerificationCode") Optional<String> overrideCode,
        @RequestParam(value = "force", defaultValue = "false") boolean force
    ) {
        Optional<String> forceUpdateCode = force ? Optional.of(resourceCacheService.getResourceStats().expectedForceUpdateVerificationCode()) : overrideCode;
        var applied = resourceCacheService.updateFullResourceCache(forceUpdateCode);

        return ResponseEntity
                .status(applied ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(Map.of("applied", applied));
    }
}
