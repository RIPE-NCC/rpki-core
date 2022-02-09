package net.ripe.rpki.ui.admin;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.GenerateOfflineCARepublishRequestCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class AllResourcesCaManagementPanel extends Panel {

    private static final long serialVersionUID = 1L;

    @SpringBean
    private RepositoryConfiguration repositoryConfiguration;

    @SpringBean
    private CertificateAuthorityViewService caViewService;

    @SpringBean
    private ResourceLookupService resourceLookupService;

    @SpringBean
    private CommandService commandService;

    @SpringBean(name = BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE)
    private BackgroundService allCertificateUpdateService;

    public AllResourcesCaManagementPanel(String id, CertificateAuthorityData allResourcesCA) {
        super(id);

        addJustRepublishButton();

        addManageKeysLifeCycleButton(allResourcesCA);
    }

    private void addManageKeysLifeCycleButton(CertificateAuthorityData allResourcesCA) {
        KeyPairStatus overallStatus = overallKeyPairLifeCyclePhase(allResourcesCA);

        switch (overallStatus) {
            case OLD:
                add(new RevokeOldKeysPanel("manageKeys", allResourcesCA));
                break;
            case PENDING:
                add(new ActivatePendingKeysPanel("manageKeys", allResourcesCA));
                break;
            case CURRENT:
                add(new InitiateRollingAllKeysPanel("manageKeys", allResourcesCA));
                break;
            default:
                add(new WebMarkupContainer("manageKeys").setVisible(false));
                break;
        }
    }

    private void addJustRepublishButton() {
        Link<Object> republishLink = new Link<Object>("republish") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick() {
                X500Principal allResourcesCaName = repositoryConfiguration.getAllResourcesCaPrincipal();
                CertificateAuthorityData allResourcesCaData = caViewService.findCertificateAuthorityByName(allResourcesCaName);

                GenerateOfflineCARepublishRequestCommand republishCommand = new GenerateOfflineCARepublishRequestCommand(allResourcesCaData.getVersionedId());
                commandService.execute(republishCommand);

                setResponsePage(UpstreamCaManagementPage.class);
            }
        };
        add(republishLink);
        republishLink.setVisible(true);
    }


    private KeyPairStatus overallKeyPairLifeCyclePhase(CertificateAuthorityData allResourcesCA) {
        if (hasKeyWithStatus(allResourcesCA.getKeys(), KeyPairStatus.OLD)) {
            return KeyPairStatus.OLD;
        }

        if (hasKeyWithStatus(allResourcesCA.getKeys(), KeyPairStatus.PENDING)) {
            return KeyPairStatus.PENDING;
        }

        if (hasKeyWithStatus(allResourcesCA.getKeys(), KeyPairStatus.CURRENT)) {
            return KeyPairStatus.CURRENT;
        }

        return KeyPairStatus.NEW; // Can happen if first certificate sign request is refused
    }

    private boolean hasKeyWithStatus(Collection<KeyPairData> keys, KeyPairStatus expectedStatus) {
        return keys.stream().anyMatch(key -> key.getStatus() == expectedStatus);
    }

    public class RevokeOldKeysPanel extends Panel {

        private static final long serialVersionUID = 1L;

        public RevokeOldKeysPanel(String id, final CertificateAuthorityData allResourcesCA) {
            super(id);

            add(new Link<Object>("revokeOld") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick() {
                    commandService.execute(new KeyManagementRevokeOldKeysCommand(allResourcesCA.getVersionedId()));
                    setResponsePage(UpstreamCaManagementPage.class);
                }
            });
        }
    }

    public class ActivatePendingKeysPanel extends Panel {

        private static final long serialVersionUID = 1L;

        public ActivatePendingKeysPanel(String id, final CertificateAuthorityData allResourcesCA) {
            super(id);

            add(new Link<Object>("activatePending") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick() {
                    commandService.execute(KeyManagementActivatePendingKeysCommand.manualActivationCommand(allResourcesCA.getVersionedId()));
                    updateProductionCaCertificates();
                    setResponsePage(UpstreamCaManagementPage.class);
                }
            });
        }

        private void updateProductionCaCertificates() {
            X500Principal productionCaName = repositoryConfiguration.getProductionCaPrincipal();
            CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaName);

            try {
                allCertificateUpdateService.execute();
            } catch (RuntimeException e) {
                log.error("Error for CA '" + productionCa.getName() + "': " + e.getMessage(), e);
            }
        }
    }

    public class InitiateRollingAllKeysPanel extends Panel {

        private static final long serialVersionUID = 1L;

        public InitiateRollingAllKeysPanel(String id, final CertificateAuthorityData allResourcesCA) {
            super(id);

            add(new Link<Object>("initiateRolls") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick() {
                    commandService.execute(new KeyManagementInitiateRollCommand(allResourcesCA.getVersionedId(), 0));
                    setResponsePage(UpstreamCaManagementPage.class);
                }
            });
        }
    }


//    public class ProductionCaCertifiableSpacePanel extends Panel {
//
//        private static final long serialVersionUID = 1L;
//
//        public ProductionCaCertifiableSpacePanel(String id, final CertificateAuthorityData allResourcesCA) {
//            super(id);
//            final ResourceClassMap certifiableResources = resourceLookupService.lookupProductionCaResources();
//            final ResourceClassMap certifiedResources = allResourcesCA.getResources().toResourceClassMap();
//            final ResourceClassMap toAdd = certifiableResources.minus(certifiedResources);
//            final ResourceClassMap toRemove = certifiedResources.minus(certifiableResources);
//
//            List<String> classNames = new ArrayList<>(certifiableResources.getResourceClasses().keySet());
//            ListView resourceClassList = new ListView<String>("resourceClassList", classNames) {
//                @Override
//                protected void populateItem(ListItem<String> item) {
//                    String resourceClass = item.getModelObject();
//                    IpResourceSet resourcesToAdd = toAdd.getResources(resourceClass);
//                    IpResourceSet resourcesToRemove = toRemove.getResources(resourceClass);
//                    boolean resourcesWillChange = !resourcesToAdd.isEmpty() || !resourcesToRemove.isEmpty();
//
//                    item.add(new Label("name", resourceClass));
//                    item.add(new Label("certified", certifiedResources.getResources(resourceClass).toString()));
//                    item.add(new Label("added", resourcesToAdd.toString()));
//                    item.add(new Label("removed", resourcesToRemove.toString()));
//                    item.add(new Label("certifiable", resourcesWillChange ? certifiableResources.getResources(resourceClass).toString() : ""));
//                }
//            };
//            add(resourceClassList);
//            addAcceptNewResourcesButton(certifiableResources);
//        }
//
//        private void addAcceptNewResourcesButton(final ResourceClassMap certifiableResources) {
//            add(new Link<Object>("signRequest") {
//                private static final long serialVersionUID = 1L;
//
//                @Override
//                public void onClick() {
//                    X500Principal productionCaName = certificationConfiguration.getProductionCaName();
//                    CertificateAuthorityData productionCaData = caViewService.findCertificateAuthorityByName(productionCaName);
//
//                    commandService.execute(new ProductionCaResourcesCommand(productionCaData.getVersionedId(), certifiableResources));
//                    setResponsePage(UpstreamCaManagementPage.class);
//                }
//
//            });
//        }
//    }
}
