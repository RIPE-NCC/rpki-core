package net.ripe.rpki.ui.security;

import net.ripe.rpki.server.api.security.CertificationUserId;
import net.ripe.rpki.server.api.security.RoleBasedAuthenticationStrategy;
import net.ripe.rpki.server.api.security.RunAsUser;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuthenticationStrategy implements RoleBasedAuthenticationStrategy {
    @Override
    public boolean isAuthenticatedUserInRole(String role) {
        return true;
    }

    @Override
    public CertificationUserId getAuthenticatedUser() {
        RunAsUser runAsUser = RunAsUserHolder.get();
        return runAsUser != null ? runAsUser.getCertificationUserId() : CertificationUserId.SYSTEM;
    }

    @Override
    public CertificationUserId getOriginalUserId() {
        RunAsUser runAsUser = RunAsUserHolder.get();
        return runAsUser != null ? runAsUser.getCertificationUserId() : CertificationUserId.SYSTEM;
    }
}
