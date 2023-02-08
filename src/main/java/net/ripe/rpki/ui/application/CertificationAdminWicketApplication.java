package net.ripe.rpki.ui.application;

import com.google.common.collect.ImmutableMap;
import lombok.NonNull;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import net.ripe.rpki.ui.admin.DeleteCAPage;
import net.ripe.rpki.ui.admin.ProvisioningIdentityDetailsPage;
import net.ripe.rpki.ui.admin.SystemStatusPage;
import net.ripe.rpki.ui.admin.UpstreamCaManagementPage;
import net.ripe.rpki.ui.audit.CertificateAuthorityHistoryPage;
import org.apache.wicket.*;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;


public class CertificationAdminWicketApplication extends WebApplication {

    public static final ImmutableMap<String, Class<? extends Page>> BOOKMARKABLE_PAGES =
        ImmutableMap.<String, Class<? extends Page>>builder()
            .put("/History", CertificateAuthorityHistoryPage.class)
            .put("/SystemStatusPage", SystemStatusPage.class)
            .put("/DeleteCAPage", DeleteCAPage.class)
            .put("/UpstreamCaManagementPage", UpstreamCaManagementPage.class)
            .put("/provisioning-identity-details", ProvisioningIdentityDetailsPage.class)
            .put("/history", CertificateAuthorityHistoryPage.class)
            .build();

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

        BOOKMARKABLE_PAGES.forEach(this::mountBookmarkablePage);
    }

    public static CertificationAdminWicketApplication get() {
        return (CertificationAdminWicketApplication) WebApplication.get();
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

    public ApplicationContext getApplicationContext() {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(this.getServletContext());
    }

    @Override
    public RequestCycle newRequestCycle(final Request request, final Response response) {
        return new CertificationAdminWebRequestCycle(this, (WebRequest) request, (WebResponse) response);
    }
}
