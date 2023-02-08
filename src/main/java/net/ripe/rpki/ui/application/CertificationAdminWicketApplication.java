package net.ripe.rpki.ui.application;

import lombok.NonNull;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
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
import org.apache.wicket.authentication.AuthenticatedWebApplication;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.WebSession;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;


public class CertificationAdminWicketApplication extends AuthenticatedWebApplication {

    private String configurationType = Application.DEPLOYMENT;

    public static RepositoryConfiguration getRepositoryConfiguration() {
        return getBean(RepositoryConfiguration.class);
    }

    public static CertificateAuthorityViewService getCaViewService() {
        return getBean(CertificateAuthorityViewService.class);
    }

    public static CommandService getCommandService() {
        return getBean(CommandService.class);
    }

    public static BackgroundService getAllCertificateUpdateService() {
        return getBean(AllCaCertificateUpdateServiceBean.class);
    }

    @NonNull
    public static <T> T getBean(Class<T> requiredType) {
        return get().getApplicationContext().getBean(requiredType);
    }

    @NonNull
    public static <T> T getBean(String name, Class<T> requiredType) {
        return get().getApplicationContext().getBean(name, requiredType);
    }

    @Override
    protected void init() {
        super.init();

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

    public ApplicationContext getApplicationContext() {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(this.getServletContext());
    }

    @Override
    public RequestCycle newRequestCycle(final Request request, final Response response) {
        return new CertificationAdminWebRequestCycle(this, (WebRequest) request, (WebResponse) response);
    }
}
