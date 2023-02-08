package net.ripe.rpki.ui.application;

import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.authorization.IAuthorizationStrategy;
import org.apache.wicket.protocol.http.WebSession;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

public class CertificationAdminWicketApplicationStub extends CertificationAdminWicketApplication {

    private ApplicationContext applicationContext;
    private Environment environment;

    public CertificationAdminWicketApplicationStub(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @Override
    protected void init() {
        super.init();

        getSecuritySettings().setAuthorizationStrategy(IAuthorizationStrategy.ALLOW_ALL);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public WebSession newSession(Request request, Response response) {
        return new CertificationAdminWebSession(request) {
            {
                signIn(true);
            }
        };
    }
}
