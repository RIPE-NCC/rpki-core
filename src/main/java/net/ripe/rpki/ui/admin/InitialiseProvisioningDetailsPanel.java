package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.InitialiseMyIdentityMaterialCommand;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;

import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCommandService;

public class InitialiseProvisioningDetailsPanel extends Panel {

    static final String GENERATE_PROVISIONING_DETAILS_LINK = "generateProvisioningDetailsLink";

    private static final long serialVersionUID = 1L;

    public InitialiseProvisioningDetailsPanel(String id, final VersionedId caId) {
        super(id);

        add(new Link<Object>(GENERATE_PROVISIONING_DETAILS_LINK) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick() {
                getCommandService().execute(new InitialiseMyIdentityMaterialCommand(caId));
                setResponsePage(ProvisioningIdentityDetailsPage.class);
            }
        });
    }
}
