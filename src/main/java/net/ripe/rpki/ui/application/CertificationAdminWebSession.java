package net.ripe.rpki.ui.application;

import net.ripe.rpki.server.api.configuration.RepositoryConfigurationBean;
import net.ripe.rpki.server.api.security.CertificationRoles;
import org.apache.wicket.Request;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.authorization.strategies.role.Roles;

public class CertificationAdminWebSession extends AuthenticatedWebSession {

    private static final long serialVersionUID = 1L;

    public CertificationAdminWebSession(Request request) {
        super(request);
    }

    @Override
    public boolean authenticate(String login, String password) {
        return RepositoryConfigurationBean.checkAdminPassword(password);
    }

    @Override
    public Roles getRoles() {
        return new Roles(new String[]{
                Roles.ADMIN,
                CertificationRoles.ROLE_CA_OPERATOR
        });
    }

    public static CertificationAdminWebSession get() {
        return (CertificationAdminWebSession) AuthenticatedWebSession.get();
    }
}
