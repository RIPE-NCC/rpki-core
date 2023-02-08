package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebMarkupContainer;

public class ProvisioningIdentityDetailsPage extends AdminCertificationBasePage {

    public static final String INITIALISE_PROVISIONING_ID_PANEL = "initialiseProvisioningIdPanel";
    public static final String SHOW_PROVISIONING_DETAILS_PANEL = "showProvisioningDetailsPanel";

    private static final String TITLE = "Provisioning Details";

    public ProvisioningIdentityDetailsPage(PageParameters parameters) {
        super(TITLE, parameters);

        ProvisioningIdentityCertificate identityMaterial = getBean(ProvisioningIdentityViewService.class).findProvisioningIdentityMaterial();

        if (identityMaterial == null) {
            showInitialiseProvisioningPanelOnly();
        } else {
            showProvisioningDetailsPanelOnly(identityMaterial);
        }

    }

    private void showInitialiseProvisioningPanelOnly() {
        add(new WebMarkupContainer(SHOW_PROVISIONING_DETAILS_PANEL).setVisible(false));
        add(new InitialiseProvisioningDetailsPanel(INITIALISE_PROVISIONING_ID_PANEL, getCurrentCertificateAuthority().getVersionedId()));
    }

    private void showProvisioningDetailsPanelOnly(ProvisioningIdentityCertificate identityMaterial) {
        add(new ShowProvisioningDetailsPanel(SHOW_PROVISIONING_DETAILS_PANEL, identityMaterial));
        add(new WebMarkupContainer(INITIALISE_PROVISIONING_ID_PANEL).setVisible(false));
    }


}
