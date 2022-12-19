package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.ui.audit.CommandListPanel;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import org.apache.commons.lang.Validate;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.List;

public class DeleteCAPage extends AdminCertificationBasePage {
    @SpringBean
    private CommandService commandService;

    public DeleteCAPage(PageParameters parameters) {
        super("Delete Certificate Authority", parameters);
        replaceCurrentCertificateAuthority(parameters);

        addFeedbackPanel();

        boolean isCaIdSpecified = parameters.containsKey("caName");
        addCaNameForm(!isCaIdSpecified);
        addHistoryPanel(isCaIdSpecified);
        addDeleteForm(isCaIdSpecified);

        if (isCaIdSpecified) {
        	info("About to delete CA: " + getCurrentCertificateAuthority().getName().getName());
        }
    }

    /**
     * Hacky parsing of what the user meant.
     */
    private X500Principal adjustName(String name) {
        if (name.startsWith("CN=")) {
            return new X500Principal(name);
        }
        return CaName.parse(name).getPrincipal();
    }

    private void replaceCurrentCertificateAuthority(PageParameters parameters) {
        if (parameters != null && parameters.containsKey("caName")) {
            final X500Principal caName = adjustName(String.valueOf(parameters.get("caName")));
            setCurrentCertificateAuthority(caViewService.findCertificateAuthorityByName(caName));
        }
    }

    private void addCaNameForm(boolean visible) {
        Form<?> form = new CaNameForm("caNameForm");
        add(form);
        form.setVisible(visible);
    }

    private void addDeleteForm(boolean visible) {
        Form<?> form = new DeleteForm("deleteForm");
        add(form);
        form.setVisible(visible);
    }

    private void addFeedbackPanel() {
        FeedbackPanel feedbackPanel = new FeedbackPanel("feedback");
        feedbackPanel.setOutputMarkupPlaceholderTag(true);
        add(feedbackPanel);
    }

    private void addHistoryPanel(boolean visible) {
        CommandListPanel commandListPanel = new CommandListPanel("commandListPanel", findCommandHistory());
        add(commandListPanel);
        commandListPanel.setVisible(visible);
    }

    private List<CommandAuditData> findCommandHistory() {
        CertificateAuthorityData ca = getCurrentCertificateAuthority();
        if (ca == null) {
            return Collections.emptyList();
        }
        return caViewService.findMostRecentCommandsForCa(ca.getId());
    }

    private class CaNameForm extends Form<Void> {
        private static final long serialVersionUID = 1L;
        private RequiredTextField<String> textField;

        public CaNameForm(String id) {
            super(id);
            textField = new RequiredTextField<>("caName", Model.of(), String.class);
            add(textField);
            add(new Button("findButton"));
        }

        @Override
        protected void onSubmit() {
            Long caId = caViewService.findCertificateAuthorityIdByName(adjustName(textField.getValue()));
            if (caId != null) {
                PageParameters caNameField = new PageParameters();
                caNameField.add("caName", textField.getValue());
                setResponsePage(DeleteCAPage.class, caNameField);
            } else {
                error("Certificate Authority for this CA name does not exist.");
            }
        }
    }

    private class DeleteForm extends Form<Void> {
        private static final long serialVersionUID = 1L;

        public DeleteForm(String id) {
            super(id);

            addDeleteButton();
            addBackButton();
        }

        private void addDeleteButton() {
            Button deleteButton = new Button("deleteButton") {
                private static final long serialVersionUID = 1L;
                @Override
                protected void onComponentTag(ComponentTag tag) {
                    tag.getAttributes().put("onclick", "return getConfirmation()");
                    tag.setModified(true);
                }
            };
            add(deleteButton);

            IHeaderContributor headerContributor = new IHeaderContributor() {
                private static final long serialVersionUID = 1L;
                @Override
                public void renderHead(IHeaderResponse response) {
                    String js = "function getConfirmation() {"
                            + "return confirm('You are about to permanently delete a CA. Are you sure?');" + "}";
                    response.renderJavascript(js, "confirmFunctionId");
                }
            };
            deleteButton.add(new HeaderContributor(headerContributor));
        }

        private void addBackButton() {
            Button backButton = new Button("backButton") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onSubmit() {
                    setResponsePage(DeleteCAPage.class);
                }
            };
            backButton.setDefaultFormProcessing(false);
            add(backButton);
        }

        @Override
        protected void onSubmit() {
            try {
                CertificateAuthorityData certificateAuthority = getCurrentCertificateAuthority();
                Validate.isTrue(certificateAuthority.getType() != CertificateAuthorityType.ROOT, "Root CA removal attempt!");
                Validate.isTrue(certificateAuthority.getType() != CertificateAuthorityType.ALL_RESOURCES, "All Resources CA removal attempt!");
                commandService.execute(new DeleteCertificateAuthorityCommand(certificateAuthority.getVersionedId(), certificateAuthority.getName()));
                info("Deleted CA " + certificateAuthority.getName());
            } catch (Exception ex) {
                error("" + ex.getMessage());
            }
        }
    }
}
