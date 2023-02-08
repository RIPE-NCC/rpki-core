package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;

import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCaViewService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCommandService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getRepositoryConfiguration;

public class AllResourcesCaRequestManagementPanel extends Panel {

    private static final long serialVersionUID = 1L;

    public AllResourcesCaRequestManagementPanel(String id) {
        super(id);


        final ManagedCertificateAuthorityData allResourcesCA  = (ManagedCertificateAuthorityData) getCaViewService().findCertificateAuthorityByName(getRepositoryConfiguration().getAllResourcesCaPrincipal());
        if(allResourcesCA != null) {
            add(new AllResourcesCaManagementPanel("managementPanel", allResourcesCA));
        }

        add(new Link<Object>("signRequest") {
            private static final long serialVersionUID1 = 1L;

            @Override
            public void onClick() {
                final CertificateAuthorityData allResourcesCa = getCaViewService().findCertificateAuthorityByName(getRepositoryConfiguration().getAllResourcesCaPrincipal());
                getCommandService().execute(new AllResourcesCaResourcesCommand(allResourcesCa.getVersionedId()));
                setResponsePage(UpstreamCaManagementPage.class);
            }
        });


    }
}
