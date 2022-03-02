package net.ripe.rpki.ui.admin;

import com.google.common.base.Stopwatch;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import net.ripe.rpki.ui.configuration.UiConfiguration;
import net.ripe.rpki.ui.util.WicketUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class SystemStatusPage extends AdminCertificationBasePage {

    private static final String INFO_PARAM_KEY = "info";

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusPage.class);

    @SpringBean
    private UiConfiguration certificationConfiguration;

    @SpringBean(name = "manifestCrlUpdateService")
    private BackgroundService manifestCrlUpdateService;

    @SpringBean(name = "publicRepositoryPublicationService")
    private BackgroundService publicRepositoryPublicationService;

    @SpringBean(name = "publicRepositoryRsyncService")
    private BackgroundService publicRepositoryRsyncService;

    @SpringBean(name = "publicRepositoryRrdpService")
    private BackgroundService publicRepositoryRrdpService;

    @SpringBean(name = "allCertificateUpdateService")
    private BackgroundService allCertificateUpdateService;

    @SpringBean(name = "productionCaKeyRolloverManagementService")
    private BackgroundService productionCaKeyRolloverManagementService;

    @SpringBean(name = "memberKeyRolloverManagementService")
    private BackgroundService memberKeyRolloverManagementService;

    @SpringBean(name = "keyPairActivationManagementService")
    private BackgroundService keyPairActivationManagementService;

    @SpringBean(name = "keyPairRevocationManagementService")
    private BackgroundService keyPairRevocationManagementService;

    @SpringBean(name = "certificateExpirationService")
    private BackgroundService certificateExpirationService;

    @SpringBean(name = "risWhoisUpdateService")
    private BackgroundService risWhoisUpdateService;

    @SpringBean(name = "roaAlertBackgroundServiceDaily")
    private BackgroundService roaAlertBackgroundService;

    @SpringBean(name = "resourceCacheUpdateService")
    private BackgroundService resourceCacheUpdateService;

    @SpringBean(name = "publishedObjectCleanUpService")
    private BackgroundService publishedObjectCleanUpService;

    @SpringBean(name = "caCleanUpService")
    private BackgroundService caCleanUpService;

    @SpringBean
    private ActiveNodeService activeNodeService;

    public SystemStatusPage(PageParameters parameters) {
        super("System Status", parameters);

        addFeedbackPanel();
        addConfigParamBits();
        addBackgroundServicesBits();

        showInfoMessageIfNeeded(parameters);
    }

    private void showInfoMessageIfNeeded(PageParameters parameters) {
        if (parameters.containsKey(INFO_PARAM_KEY)) {
            try {
                info(URLDecoder.decode(parameters.getString(INFO_PARAM_KEY), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOG.error("", e);
            }
        }
    }

    private void addFeedbackPanel() {
        FeedbackPanel feedbackPanel = new FeedbackPanel("feedback");
        feedbackPanel.setOutputMarkupPlaceholderTag(true);
        add(feedbackPanel);
    }

    private void addConfigParamBits() {
        add(new Label("localRepositoryDirectory", repositoryConfiguration.getLocalRepositoryDirectory().getAbsolutePath()));
        add(new Label("publicRepositoryUri", repositoryConfiguration.getPublicRepositoryUri().toASCIIString()));

        String instanceName = activeNodeService.getCurrentNodeName();
        add(new Label("instancename", instanceName));
        add(WicketUtils.getStatusImage("activeNodeImage", instanceName.equals(activeNodeService.getActiveNodeName())));

        add(new ActiveNodeForm("activeNodeForm", getActiveNodeModel()));
    }

    private class ActiveNodeForm extends Form<String> {

        private static final long serialVersionUID = 1L;
        private TextField<String> activeNode;

        public ActiveNodeForm(String id, IModel<String> model) {
            super(id, model);
            activeNode = new TextField<>("activeNode", getActiveNodeModel(), String.class);
            add(activeNode);
        }

        @Override
        protected void onSubmit() {
            String message = "Active node name has been updated";
            try {
                message = URLEncoder.encode("Active node name has been updated to '" + activeNode.getValue() + "'", "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error("", e);
            }

            PageParameters pageParameters = new PageParameters(INFO_PARAM_KEY + "=" + message);
            setResponsePage(SystemStatusPage.class, pageParameters);
        }
    }

    private IModel<String> getActiveNodeModel() {
        return new Model<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                return activeNodeService.getActiveNodeName();
            }

            @Override
            public void setObject(String value) {
                activeNodeService.setActiveNodeName(value);
            }
        };
    }

    private void addBackgroundServicesBits() {
        add(new Label("repositoryServiceStatus", serviceLabel(manifestCrlUpdateService)));
        add(new Label("repositoryPublicationStatus", serviceLabel(publicRepositoryPublicationService)));
        add(new Label("repositoryRsyncStatus", serviceLabel(publicRepositoryRsyncService)));
        add(new Label("repositoryRrdpStatus", serviceLabel(publicRepositoryRrdpService)));
        add(new Label("allCertificateUpdateServiceStatus", serviceLabel(allCertificateUpdateService)));
        add(new Label("productionKeyRolloverServiceStatus", serviceLabel(productionCaKeyRolloverManagementService)));
        add(new Label("memberKeyRolloverServiceStatus", serviceLabel(memberKeyRolloverManagementService)));
        add(new Label("keyPairActivationServiceStatus", serviceLabel(keyPairActivationManagementService)));
        add(new Label("keyPairRevocationServiceStatus", serviceLabel(keyPairRevocationManagementService)));
        add(new Label("certificateExpirationServiceStatus", serviceLabel(certificateExpirationService)));
        add(new Label("bgpRisUpdateServiceStatus", serviceLabel(risWhoisUpdateService)));
        add(new Label("roaAlertBackgroundServiceStatus", serviceLabel(roaAlertBackgroundService)));
        add(new Label("resourceCacheUpdateServiceStatus", serviceLabel(resourceCacheUpdateService)));
        add(new Label("publishedObjectCleanUpServiceStatus", serviceLabel(publishedObjectCleanUpService)));
        add(new Label("caCleanUpServiceStatus", serviceLabel(caCleanUpService)));

        addBackgroundServiceExecuteLink("updateManifestLink", manifestCrlUpdateService);
        addBackgroundServiceExecuteLink("updatePublicationStatusLink", publicRepositoryPublicationService);
        addBackgroundServiceExecuteLink("updateRsyncLink", publicRepositoryRsyncService);
        addBackgroundServiceExecuteLink("updateRrdpLink", publicRepositoryRrdpService);
        addBackgroundServiceExecuteLink("activatePendingKeyPairsLink", keyPairActivationManagementService);
        addBackgroundServiceExecuteLink("revokeOldKeyPairsLink", keyPairRevocationManagementService);
        addBackgroundServiceExecuteLink("certificateExpirationServiceLink", certificateExpirationService);
        addBackgroundServiceExecuteLink("productionCaRollOverLink", productionCaKeyRolloverManagementService);
        addBackgroundServiceExecuteLink("memberRollOverLink", memberKeyRolloverManagementService);
        addBackgroundServiceExecuteLink("bgpRisUpdateLink", risWhoisUpdateService);
        addBackgroundServiceExecuteLink("roaAlertBackgroundServiceLink", roaAlertBackgroundService);
        addBackgroundServiceExecuteLink("resourceCacheUpdateServiceLink", resourceCacheUpdateService);
        addBackgroundServiceExecuteLink("updateResourcesLink", allCertificateUpdateService);
        addBackgroundServiceExecuteLink("publishedObjectCleanUpServiceLink", publishedObjectCleanUpService);
        addBackgroundServiceExecuteLink("caCleanUpServiceLink", caCleanUpService);
    }

    private String serviceLabel(BackgroundService service) {
        if (service.isActive()) {
            return service.getStatus();
        }
        return "Inactive";
    }

    private void addBackgroundServiceExecuteLink(String id, final BackgroundService backgroundService) {
        final Link<Object> link = new Link<Object>(id) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick() {
                Stopwatch stopwatch = Stopwatch.createStarted();
                backgroundService.execute();
                logAndDisplayMessage(backgroundService.getName() + " has been executed manually (" + stopwatch + ")");
            }
        };
        link.setVisibilityAllowed(!backgroundService.isRunning() && backgroundService.isActive());
        add(link);
    }

    private void logAndDisplayMessage(String message) {
        info(message);
        LOG.info(message);
    }
}
