package net.ripe.rpki.server.api.security;

import lombok.Getter;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import org.apache.commons.lang.Validate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class RunAsUser extends ValueObjectSupport {

    private final CertificationUserId userId;
    @Getter
    private final String friendlyName;
    @Getter
    private final List<String> roles;

    /**
     * Admin User used for elevated privileges for background jobs
     */
    public static final RunAsUser ADMIN = new RunAsUser(CertificationUserId.SYSTEM,
            "system", Collections.singletonList(CertificationRoles.ROLE_CA_OPERATOR));

    private RunAsUser(CertificationUserId userId, String friendlyName, List<String> roles) {
        Validate.notNull(userId);
        Validate.notNull(friendlyName);
        Validate.notNull(roles);
        this.userId = userId;
        this.friendlyName = friendlyName;
        this.roles = Collections.unmodifiableList(roles);
    }

    public static RunAsUser operator(UUID uuid) {
        return new RunAsUser(new CertificationUserId(uuid), uuid.toString(),
                Collections.singletonList(CertificationRoles.ROLE_CA_OPERATOR));
    }

    public CertificationUserId getCertificationUserId() {
        return userId;
    }

}
