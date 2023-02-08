package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.domain.response.TaResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorResponseSerializer;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.OfflineResponseProcessorException;
import net.ripe.rpki.ui.commons.FileUploadUtils;
import org.apache.wicket.markup.html.WebResource;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;

import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getAllCertificateUpdateService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCaViewService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCommandService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getRepositoryConfiguration;

public class PendingRequestPanel extends Panel {

    private static final long serialVersionUID = 1L;

    public PendingRequestPanel(String id, TrustAnchorRequest request) {
        super(id);
        if (request != null) {
            add(new PendingRequestExistsPanel("content", request));
        } else {
            add(new NoPendingRequestPanel("content"));
        }
    }

    private class NoPendingRequestPanel extends Panel {
        private static final long serialVersionUID = 1L;

        public NoPendingRequestPanel(String id) {
            super(id);
        }
    }

    private class PendingRequestExistsPanel extends Panel {
        private static final long serialVersionUID = 1L;

        public PendingRequestExistsPanel(String id, TrustAnchorRequest request) {
            super(id);

            TrustAnchorRequestResource resource = new TrustAnchorRequestResource(request);
            add(new Label("requestDescription", "Pending: " + resource.getFileName()));
            add(new ResourceLink<WebResource>("downloadLink", resource));
            add(new OfflineResponseUploadForm("offlineResponseUploadForm"));
        }
    }

    private static class OfflineResponseUploadForm extends Form<TaResponse> {

        private static final long serialVersionUID = 1L;
        private static final Logger LOG = LoggerFactory.getLogger(OfflineResponseUploadForm.class);

        private FileUploadField fileUploadField;

        public OfflineResponseUploadForm(String id) {
            super(id);
            setMultiPart(true);
            fileUploadField = new FileUploadField("offlineResponseUploadFile");
            add(fileUploadField);
        }

        @Override
        protected void onSubmit() {
            try {
                FileUpload fileUpload = fileUploadField.getFileUpload();
                if (fileUpload != null) {
                    String content = FileUploadUtils.convertUploadedFileToString(fileUpload);
                    TrustAnchorResponse responseObject = new TrustAnchorResponseSerializer().deserialize(content);
                    getCommandService().execute(createCommand(responseObject));

                    getAllCertificateUpdateService().execute(Collections.emptyMap());

                    setResponsePage(UpstreamCaManagementPage.class);
                }
            } catch (OfflineResponseProcessorException offlineResponseProcessorException) {
                error("Response file was rejected. Reason: " + offlineResponseProcessorException.getMessage());
                LOG.error("Response file was rejected. Reason: " + offlineResponseProcessorException.getMessage());
            } catch (Exception e) {
                error("Exception while trying to process uploaded file (check that it's a valid response xml file): " + e);
                LOG.error("Exception processing uploaded offline CA response file", e);
            }
        }

        private ProcessTrustAnchorResponseCommand createCommand(TrustAnchorResponse response) {
            X500Principal allResourcesCaName = getRepositoryConfiguration().getAllResourcesCaPrincipal();
            CertificateAuthorityData allResourcesCa = getCaViewService().findCertificateAuthorityByName(allResourcesCaName);
            return new ProcessTrustAnchorResponseCommand(allResourcesCa.getVersionedId(), response);
        }

    }

}
