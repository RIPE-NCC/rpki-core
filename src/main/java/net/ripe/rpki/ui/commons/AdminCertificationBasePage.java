package net.ripe.rpki.ui.commons;

import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.ui.admin.UpstreamCaManagementPage;
import net.ripe.rpki.ui.application.CertificationAdminWebSession;
import net.ripe.rpki.ui.application.CertificationAdminWicketApplication;
import net.ripe.rpki.ui.ca.CreateProductionCaPage;
import net.ripe.rpki.ui.configuration.UiConfiguration;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.spring.injection.annot.SpringBean;


public abstract class AdminCertificationBasePage extends MinimalRPKIBasePage {

    private static final long serialVersionUID = 1L;

    protected static final String TITLE_PREFIX = "Resource Certification - ";

    @SpringBean
    protected CertificateAuthorityViewService caViewService;

    @SpringBean
    protected RepositoryConfiguration repositoryConfiguration;
    @SpringBean
    protected UiConfiguration uiConfiguration;

    private CertificateAuthorityData currentCertificateAuthority;

    private String pageTitle;

    public AdminCertificationBasePage(PageParameters parameters) {
        this("No title provided", parameters);
    }

    public AdminCertificationBasePage(String pageTitle, PageParameters parameters) {
        this(pageTitle, parameters, true);
    }

    public AdminCertificationBasePage(String pageTitle, PageParameters parameters, boolean interceptNoCA) {
        super(parameters);
        this.pageTitle = pageTitle;

        //if user is not signed in, redirect to sign in page
        CertificationAdminWicketApplication app = CertificationAdminWicketApplication.get();
        if (!AuthenticatedWebSession.get().isSignedIn()) {
            app.onUnauthorizedInstantiation(this);
        }

        if (interceptNoCA) {
            if (!allResourceCaExists()) {
                setResponsePage(UpstreamCaManagementPage.class);
            } else if (!productionCaExists()) {
                setResponsePage(CreateProductionCaPage.class);
            }
        }

        setCurrentCertificateAuthority(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal()));

        addDeploymentEnvironmentBanner();
    }

    @Override
    public CertificationAdminWebSession getSession() {
        return CertificationAdminWebSession.get();
    }

    private void addDeploymentEnvironmentBanner() {
        String deploymentEnvironmentBannerImage = uiConfiguration.getDeploymentEnvironmentBannerImage();
//        ContextImage image = new ContextImage("envBanner", deploymentEnvironmentBannerImage);
        Label stripe = new Label("envStripe");
//        add(image);
        add(stripe);

//        image.setVisible(certificationConfiguration.showEnvironmentBanner());
        stripe.setVisible(uiConfiguration.showEnvironmentStripe());
    }

    protected CertificateAuthorityData getCurrentCertificateAuthority() {
        return currentCertificateAuthority;
    }

    public void setCurrentCertificateAuthority(CertificateAuthorityData currentCertificateAuthority) {
        this.currentCertificateAuthority = currentCertificateAuthority;
    }

    protected boolean hasCurrentHostedOrRootCertificateAuthority() {
        return hasCurrentCertificateAuthorityOfType(CertificateAuthorityType.HOSTED) || hasCurrentRootCertificateAuthority();
    }

    protected boolean hasCurrentNonHostedCertificateAuthority() {
        return hasCurrentCertificateAuthorityOfType(CertificateAuthorityType.NONHOSTED);
    }

    protected boolean hasCurrentRootCertificateAuthority() {
        return hasCurrentCertificateAuthorityOfType(CertificateAuthorityType.ROOT);
    }

    protected boolean hasCurrentCertificateAuthorityOfAnyType() {
        return currentCertificateAuthority != null;
    }

    private boolean hasCurrentCertificateAuthorityOfType(CertificateAuthorityType type) {
        return hasCurrentCertificateAuthorityOfAnyType() && currentCertificateAuthority.getType() == type;
    }

    private boolean allResourceCaExists() {
        return caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal()) != null;
    }

    private boolean productionCaExists() {
        return caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal()) != null;
    }

    @Override
    protected String getPageTitle() {
        return TITLE_PREFIX + pageTitle;
    }

    @Override
    public String pageGAUrl() {
        return RequestCycle.get().getRequest().getPath();
    }

}
