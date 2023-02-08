package net.ripe.rpki.ui.admin;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.GenerateOfflineCARepublishRequestCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;

import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.Collections;

import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getAllCertificateUpdateService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCaViewService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getCommandService;
import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getRepositoryConfiguration;

@Slf4j
public class AllResourcesCaManagementPanel extends Panel {

    private static final long serialVersionUID = 1L;

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
                X500Principal allResourcesCaName = getRepositoryConfiguration().getAllResourcesCaPrincipal();
                ManagedCertificateAuthorityData allResourcesCaData = (ManagedCertificateAuthorityData) getCaViewService().findCertificateAuthorityByName(allResourcesCaName);

                GenerateOfflineCARepublishRequestCommand republishCommand = new GenerateOfflineCARepublishRequestCommand(allResourcesCaData.getVersionedId());
                getCommandService().execute(republishCommand);

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
                    getCommandService().execute(new KeyManagementRevokeOldKeysCommand(allResourcesCA.getVersionedId()));
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
                    getCommandService().execute(KeyManagementActivatePendingKeysCommand.manualActivationCommand(allResourcesCA.getVersionedId()));
                    updateProductionCaCertificates();
                    setResponsePage(UpstreamCaManagementPage.class);
                }
            });
        }

        private void updateProductionCaCertificates() {
            X500Principal productionCaName = getRepositoryConfiguration().getProductionCaPrincipal();
            CertificateAuthorityData productionCa = getCaViewService().findCertificateAuthorityByName(productionCaName);

            try {
                getAllCertificateUpdateService().execute(Collections.emptyMap());
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
                    getCommandService().execute(new KeyManagementInitiateRollCommand(allResourcesCA.getVersionedId(), 0));
                    setResponsePage(UpstreamCaManagementPage.class);
                }
            });
        }
    }
}
