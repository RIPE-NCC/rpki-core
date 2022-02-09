package net.ripe.rpki.ui.application;

import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.application.IComponentInstantiationListener;
import org.apache.wicket.authorization.IAuthorizationStrategy;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.spring.test.ApplicationContextMock;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

public class CertificationAdminWicketApplicationStub extends CertificationAdminWicketApplication {

    private ApplicationContextMock applicationContext;
    private Environment environment;

    public CertificationAdminWicketApplicationStub(ApplicationContextMock applicationContext) {
        this.applicationContext = applicationContext;
        this.environment = new StandardEnvironment();
    }

    @Override
    protected void init() {
        setUpApplicationContext();
        super.init();

        getSecuritySettings().setAuthorizationStrategy(IAuthorizationStrategy.ALLOW_ALL);
    }

    private void setUpApplicationContext() {
        applicationContext.putBean("environment", environment);

        SpringComponentInjector springComponentInjector = new SpringComponentInjector(this, applicationContext, true);
        addComponentInstantiationListener(springComponentInjector);
        setSpringComponentInjector(springComponentInjector);
    }

    @Override
    protected IComponentInstantiationListener getComponentInstantiationListener() {
        return null;
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
