package net.ripe.rpki.ui.application;

import net.ripe.rpki.ui.admin.AdminLoginPage;
import net.ripe.rpki.ui.admin.DeleteCAPage;
import net.ripe.rpki.ui.admin.ProvisioningIdentityDetailsPage;
import net.ripe.rpki.ui.admin.SystemStatusPage;
import net.ripe.rpki.ui.admin.UpstreamCaManagementPage;
import net.ripe.rpki.ui.audit.CertificateAuthorityHistoryPage;
import org.apache.wicket.Application;
import org.apache.wicket.Request;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.Response;
import org.apache.wicket.application.IComponentInstantiationListener;
import org.apache.wicket.authentication.AuthenticatedWebApplication;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.injection.web.InjectorHolder;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;


public class CertificationAdminWicketApplication extends AuthenticatedWebApplication {

    private String configurationType = Application.DEPLOYMENT;

    private SpringComponentInjector springComponentInjector;

    @Override
    protected void init() {
        super.init();

        // setup injection first
        IComponentInstantiationListener componentInstantiationListener = getComponentInstantiationListener();
        if (componentInstantiationListener != null) {
            addComponentInstantiationListener(getComponentInstantiationListener());
            InjectorHolder.getInjector().inject(this);
        }

        mountBookmarkablePage("/History", CertificateAuthorityHistoryPage.class);
        mountBookmarkablePage("/SystemStatusPage", SystemStatusPage.class);
        mountBookmarkablePage("/DeleteCAPage", DeleteCAPage.class);
        mountBookmarkablePage("/UpstreamCaManagementPage", UpstreamCaManagementPage.class);

        mountBookmarkablePage("/provisioning-identity-details", ProvisioningIdentityDetailsPage.class);
        mountBookmarkablePage("/history", CertificateAuthorityHistoryPage.class);
    }

    @Override
    protected Class<? extends AuthenticatedWebSession> getWebSessionClass() {
        return CertificationAdminWebSession.class;
    }

    public static CertificationAdminWicketApplication get() {
        return (CertificationAdminWicketApplication) AuthenticatedWebApplication.get();
    }

    public void setConfigurationType(String configurationType) {
        this.configurationType = configurationType;
    }

    @Override
    public String getConfigurationType() {
        return configurationType;
    }

    @Override
    public Class<SystemStatusPage> getHomePage() {
        return SystemStatusPage.class;
    }

    @Override
    protected Class<? extends WebPage> getSignInPageClass() {
        return AdminLoginPage.class;
    }

    @Override
    public WebSession newSession(Request request, Response response) {
        return new CertificationAdminWebSession(request);
    }

    protected IComponentInstantiationListener getComponentInstantiationListener() {
        if (springComponentInjector == null) {
            springComponentInjector = new SpringComponentInjector(this);
        }
        return springComponentInjector;
    }

    public void setSpringComponentInjector(SpringComponentInjector springComponentInjector) {
        this.springComponentInjector = springComponentInjector;
    }

    @Override
    public RequestCycle newRequestCycle(final Request request, final Response response) {
        return new CertificationAdminWebRequestCycle(this, (WebRequest) request, (WebResponse) response);
    }
}
