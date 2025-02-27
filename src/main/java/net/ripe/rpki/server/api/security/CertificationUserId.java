package net.ripe.rpki.server.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.UUID;

@AllArgsConstructor
public class CertificationUserId extends ValueObjectSupport {

    private static final UUID SYSTEM_ID = UUID.fromString("3b22801d-c151-4bcc-9298-a93df3f365d9");
    public static final CertificationUserId SYSTEM = new CertificationUserId(SYSTEM_ID);

    @Getter
    private final UUID id;
}
