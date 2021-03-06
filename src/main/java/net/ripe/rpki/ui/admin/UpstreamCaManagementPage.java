package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.ui.ca.CreateProductionCaPage;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;


public class UpstreamCaManagementPage extends AdminCertificationBasePage {

    @SpringBean
    private CertificateAuthorityViewService caViewService;

    @SpringBean
    private ActiveNodeService activeNodeService;

    @SpringBean
    private CommandService commandService;

    public UpstreamCaManagementPage(PageParameters parameters) {
        super("Requests", parameters, false);

        final CertificateAuthorityData allResourcesCA = caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());

        add(new FeedbackPanel("feedback").setOutputMarkupPlaceholderTag(true));

        final CreateAllResourcesCertificateAuthorityForm createAllResourcesCertificateAuthorityForm = new CreateAllResourcesCertificateAuthorityForm("createAllResourcesCertificateAuthorityForm");
        add(createAllResourcesCertificateAuthorityForm);
        createAllResourcesCertificateAuthorityForm.setVisible(allResourcesCA == null);

        if (allResourcesCA != null && allResourcesCA.getTrustAnchorRequest() != null) {
            add(new PendingRequestPanel("pendingRequestOrManagementPanel", allResourcesCA.getTrustAnchorRequest()));
        } else {
            final AllResourcesCaRequestManagementPanel panel = new AllResourcesCaRequestManagementPanel("pendingRequestOrManagementPanel");
            panel.setVisible(allResourcesCA != null);
            add(panel);
        }
    }


    private class CreateAllResourcesCertificateAuthorityForm extends Form<CreateProductionCaPage> {
        private static final long serialVersionUID = 1L;

        public CreateAllResourcesCertificateAuthorityForm(String id) {
            super(id, new CompoundPropertyModel<>(UpstreamCaManagementPage.this));
        }

        @Override
        protected void onSubmit() {
            try {
                commandService.execute(new CreateAllResourcesCertificateAuthorityCommand(commandService.getNextId()));
                activeNodeService.activateCurrentNode();
                setResponsePage(UpstreamCaManagementPage.class);
            } catch (CertificateAuthorityNameNotUniqueException ex) {
                error(getString("certificateAuthority.name.notUnique"));
            } catch (Exception ex) {
                error(ex.getMessage());
            }
        }
    }


}
