package net.ripe.rpki.ripencc.services.impl;

import java.util.Optional;
import java.util.UUID;

public interface AuthServiceClient {
    boolean isAvailable();

    Optional<String> getUserEmail(UUID userUuid);
}
