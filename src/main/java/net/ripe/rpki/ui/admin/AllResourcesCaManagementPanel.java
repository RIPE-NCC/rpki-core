package net.ripe.rpki.ui.admin;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.GenerateOfflineCARepublishRequestCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
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
import java.util.Collection;
import java.util.Collections;

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

    public AllResourcesCaManagementPanel(String id, ManagedCertificateAuthorityData allResourcesCA) {
        super(id);

        addJustRepublishButton();

        addManageKeysLifeCycleButton(allResourcesCA);
    }

    private void addManageKeysLifeCycleButton(ManagedCertificateAuthorityData allResourcesCA) {
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
                ManagedCertificateAuthorityData allResourcesCaData = (ManagedCertificateAuthorityData) caViewService.findCertificateAuthorityByName(allResourcesCaName);

                GenerateOfflineCARepublishRequestCommand republishCommand = new GenerateOfflineCARepublishRequestCommand(allResourcesCaData.getVersionedId());
                commandService.execute(republishCommand);

                setResponsePage(UpstreamCaManagementPage.class);
            }
        };
        add(republishLink);
        republishLink.setVisible(true);
    }


    private KeyPairStatus overallKeyPairLifeCyclePhase(ManagedCertificateAuthorityData allResourcesCA) {
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
                allCertificateUpdateService.execute(Collections.emptyMap());
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
}
