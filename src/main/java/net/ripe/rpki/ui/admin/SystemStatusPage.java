package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import net.ripe.rpki.ui.util.WicketUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class SystemStatusPage extends AdminCertificationBasePage {

    private static final String INFO_PARAM_KEY = "info";

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusPage.class);

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
        add(new Label("localRepositoryDirectory", getRepositoryConfiguration().getLocalRepositoryDirectory().getAbsolutePath()));
        add(new Label("publicRepositoryUri", getRepositoryConfiguration().getPublicRepositoryUri().toASCIIString()));

        String instanceName = getActiveNodeService().getCurrentNodeName();
        add(new Label("instancename", instanceName));
        add(WicketUtils.getStatusImage("activeNodeImage", instanceName.equals(getActiveNodeService().getActiveNodeName())));

        add(new ActiveNodeForm("activeNodeForm", getActiveNodeModel()));
    }

    public BackgroundServices getBackgroundServices() {
        return getBean(BackgroundServices.class);
    }

    public BackgroundService getManifestCrlUpdateService() {
        return getBean("manifestCrlUpdateService", BackgroundService.class);
    }

    public BackgroundService getPublicRepositoryPublicationService() {
        return getBean("publicRepositoryPublicationService", BackgroundService.class);
    }

    public BackgroundService getPublicRepositoryRsyncService() {
        return getBean("publicRepositoryRsyncService", BackgroundService.class);
    }

    public BackgroundService getPublicRepositoryRrdpService() {
        return getBean("publicRepositoryRrdpService", BackgroundService.class);
    }

    public BackgroundService getAllCertificateUpdateService() {
        return getBean("allCertificateUpdateService", BackgroundService.class);
    }

    public BackgroundService getProductionCaKeyRolloverManagementService() {
        return getBean("productionCaKeyRolloverManagementService", BackgroundService.class);
    }

    public BackgroundService getHostedCaKeyRolloverManagementService() {
        return getBean(BackgroundServices.HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE, BackgroundService.class);
    }

    public BackgroundService getKeyPairActivationManagementService() {
        return getBean("keyPairActivationManagementService", BackgroundService.class);
    }

    public BackgroundService getKeyPairRevocationManagementService() {
        return getBean("keyPairRevocationManagementService", BackgroundService.class);
    }

    public BackgroundService getCertificateExpirationService() {
        return getBean("certificateExpirationService", BackgroundService.class);
    }

    public BackgroundService getRisWhoisUpdateService() {
        return getBean("risWhoisUpdateService", BackgroundService.class);
    }

    public BackgroundService getRoaAlertBackgroundService() {
        return getBean("roaAlertBackgroundServiceDaily", BackgroundService.class);
    }

    public BackgroundService getResourceCacheUpdateService() {
        return getBean("resourceCacheUpdateService", BackgroundService.class);
    }

    public BackgroundService getPublishedObjectCleanUpService() {
        return getBean("publishedObjectCleanUpService", BackgroundService.class);
    }

    public BackgroundService getCaCleanUpService() {
        return getBean("caCleanUpService", BackgroundService.class);
    }

    public BackgroundService getPublisherSyncService() {
        return getBean("publisherSyncService", BackgroundService.class);
    }

    public ActiveNodeService getActiveNodeService() {
        return getBean(ActiveNodeService.class);
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
                return getActiveNodeService().getActiveNodeName();
            }

            @Override
            public void setObject(String value) {
                getActiveNodeService().setActiveNodeName(value);
            }
        };
    }

    private void addBackgroundServicesBits() {
        add(new Label("repositoryServiceStatus", serviceLabel(getManifestCrlUpdateService())));
        add(new Label("repositoryPublicationStatus", serviceLabel(getPublicRepositoryPublicationService())));
        add(new Label("repositoryRsyncStatus", serviceLabel(getPublicRepositoryRsyncService())));
        add(new Label("repositoryRrdpStatus", serviceLabel(getPublicRepositoryRrdpService())));
        add(new Label("allCertificateUpdateServiceStatus", serviceLabel(getAllCertificateUpdateService())));
        add(new Label("productionKeyRolloverServiceStatus", serviceLabel(getProductionCaKeyRolloverManagementService())));
        add(new Label("hostedCaKeyRolloverServiceStatus", serviceLabel(getHostedCaKeyRolloverManagementService())));
        add(new Label("keyPairActivationServiceStatus", serviceLabel(getKeyPairActivationManagementService())));
        add(new Label("keyPairRevocationServiceStatus", serviceLabel(getKeyPairRevocationManagementService())));
        add(new Label("certificateExpirationServiceStatus", serviceLabel(getCertificateExpirationService())));
        add(new Label("bgpRisUpdateServiceStatus", serviceLabel(getRisWhoisUpdateService())));
        add(new Label("roaAlertBackgroundServiceStatus", serviceLabel(getRoaAlertBackgroundService())));
        add(new Label("resourceCacheUpdateServiceStatus", serviceLabel(getResourceCacheUpdateService())));
        add(new Label("publishedObjectCleanUpServiceStatus", serviceLabel(getPublishedObjectCleanUpService())));
        add(new Label("caCleanUpServiceStatus", serviceLabel(getCaCleanUpService())));
        add(new Label("publisherSyncServiceStatus", serviceLabel(getPublisherSyncService())));

        addBackgroundServiceExecuteLink("updateManifestLink", "manifestCrlUpdateService");
        addBackgroundServiceExecuteLink("updatePublicationStatusLink", "publicRepositoryPublicationService");
        addBackgroundServiceExecuteLink("updateRsyncLink", "publicRepositoryRsyncService");
        addBackgroundServiceExecuteLink("updateRrdpLink", "publicRepositoryRrdpService");
        addBackgroundServiceExecuteLink("activatePendingKeyPairsLink", "keyPairActivationManagementService");
        addBackgroundServiceExecuteLink("revokeOldKeyPairsLink", "keyPairRevocationManagementService");
        addBackgroundServiceExecuteLink("certificateExpirationServiceLink", "certificateExpirationService");
        addBackgroundServiceExecuteLink("productionCaRollOverLink", "productionCaKeyRolloverManagementService");
        addBackgroundServiceExecuteLink("hostedCaRollOverLink", BackgroundServices.HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE);
        addBackgroundServiceExecuteLink("bgpRisUpdateLink", "risWhoisUpdateService");
        addBackgroundServiceExecuteLink("roaAlertBackgroundServiceLink", "roaAlertBackgroundServiceDaily");
        addBackgroundServiceExecuteLink("resourceCacheUpdateServiceLink", "resourceCacheUpdateService");
        addBackgroundServiceExecuteLink("updateResourcesLink", "allCertificateUpdateService");
        addBackgroundServiceExecuteLink("publishedObjectCleanUpServiceLink", "publishedObjectCleanUpService");
        addBackgroundServiceExecuteLink("caCleanUpServiceLink", "caCleanUpService");
        addBackgroundServiceExecuteLink("publisherSyncServiceLink", "publisherSyncService");
    }

    private String serviceLabel(BackgroundService service) {
        if (service.isActive()) {
            return service.getStatus();
        }
        return "Inactive";
    }

    private void addBackgroundServiceExecuteLink(String id, final String serviceName) {
        final Link<Object> link = new Link<Object>(id) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick() {
                BackgroundService backgroundService = getBean(serviceName, BackgroundService.class);
                try {
                    getBackgroundServices().trigger(serviceName);
                    logAndDisplayMessage(backgroundService.getName() + " has been triggered manually");
                    setResponsePage(SystemStatusPage.class);
                } catch (Exception e) {
                    String message = backgroundService.getName() + " failed to start: " + e;
                    error(message);
                    LOG.error("{} failed to start", backgroundService.getName(), e);
                }
            }
        };
        BackgroundService backgroundService = getBean(serviceName, BackgroundService.class);
        link.setVisibilityAllowed(!backgroundService.isWaitingOrRunning() && backgroundService.isActive());
        add(link);
    }

    private void logAndDisplayMessage(String message) {
        info(message);
        LOG.info(message);
    }
}
