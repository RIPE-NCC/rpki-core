package net.ripe.rpki.ui.ca;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.ui.admin.UpstreamCaManagementPage;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class CreateProductionCaPage extends AdminCertificationBasePage {

    @SpringBean
    private CommandService commandService;

    @SpringBean
    private ActiveNodeService activeNodeService;

    public CreateProductionCaPage(PageParameters parameters) {
        super("Create Production Certification Authority", parameters, false);
        add(new FeedbackPanel("feedbackPanel"));
        add(getCertificateAuthorityCreationForm());
    }

    private Form<CreateProductionCaPage> getCertificateAuthorityCreationForm() {
        return new CreateCertificateAuthorityForm("createCertificateAuthorityForm");
    }

    @Override
    protected String getPageTitle() {
        return "Create Production Certification Authority";
    }

    private class CreateCertificateAuthorityForm extends Form<CreateProductionCaPage> {
        private static final long serialVersionUID = 1L;

        public CreateCertificateAuthorityForm(String id) {
            super(id, new CompoundPropertyModel<>(CreateProductionCaPage.this));
        }

        @Override
        protected void onSubmit() {
            try {
                VersionedId caId = commandService.getNextId();
                commandService.execute(new CreateRootCertificateAuthorityCommand(caId));
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
