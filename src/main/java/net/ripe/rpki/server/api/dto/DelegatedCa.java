package net.ripe.rpki.server.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Optional;

public record DelegatedCa(String caName,
                          Optional<String> keyIdentifier,
                          @JsonInclude(JsonInclude.Include.NON_ABSENT)
                          Optional<Instant> lastProvisionedAt) {
}
