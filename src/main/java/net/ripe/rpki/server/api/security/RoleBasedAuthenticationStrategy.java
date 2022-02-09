package net.ripe.rpki.server.api.security;

public interface RoleBasedAuthenticationStrategy {

	boolean isAuthenticatedUserInRole(String role);

	CertificationUserId getAuthenticatedUser();

    /**
     * Returns the original user that's currently logged in, even if currently
     * running as another user with elevated permissions. (Ror example when creating a CA.)
     *
     * @return
     */
    CertificationUserId getOriginalUserId();
}
