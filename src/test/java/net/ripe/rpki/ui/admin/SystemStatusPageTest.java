package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.services.impl.background.*;
import net.ripe.rpki.ui.application.CertificationWicketTestCase;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static net.ripe.rpki.services.impl.background.BackgroundServices.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SystemStatusPageTest extends CertificationWicketTestCase {

    @MockBean
    private BackgroundServices backgroundServices;
    @MockBean(name = MANIFEST_CRL_UPDATE_SERVICE)
    private ManifestCrlUpdateServiceBean manifestCrlUpdateService;
    @MockBean(name = ALL_CA_CERTIFICATE_UPDATE_SERVICE)
    private BackgroundService allCertificateUpdateService;
    @MockBean(name = PUBLIC_REPOSITORY_PUBLICATION_SERVICE)
    private PublicRepositoryPublicationServiceBean publicRepositoryPublicationService;
    @MockBean(name = PUBLIC_REPOSITORY_RSYNC_SERVICE)
    private PublicRepositoryRsyncServiceBean publicRepositoryRsyncService;
    @MockBean(name = PUBLIC_REPOSITORY_RRDP_SERVICE)
    private PublicRepositoryRrdpServiceBean publicRepositoryRrdpService;
    @MockBean(name = PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE)
    private ProductionCaKeyRolloverManagementServiceBean productionCaKeyRolloverManagementService;
    @MockBean(name = HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE)
    private HostedCaKeyRolloverManagementServiceBean hostedCaKeyRolloverManagementService;
    @MockBean(name = KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE)
    private KeyPairActivationManagementServiceBean keyPairActivationManagementService;
    @MockBean(name = "keyPairRevocationManagementService")
    private KeyPairRevocationManagementServiceBean keyPairRevocationManagementService;
    @MockBean(name = "certificateExpirationService")
    private CertificateExpirationServiceBean certificateExpirationService;
    @MockBean(name = RIS_WHOIS_UPDATE_SERVICE)
    private RisWhoisUpdateServiceBean risWhoisUpdateService;
    @MockBean(name = "roaAlertBackgroundServiceDaily")
    private RoaAlertBackgroundServiceDailyBean roaAlertBackgroundServiceDaily;
    @MockBean(name = "resourceCacheUpdateService")
    private ResourceCacheUpdateServiceBean resourceCacheUpdateService;
    @MockBean(name = PUBLISHED_OBJECT_CLEAN_UP_SERVICE)
    private PublishedObjectCleanUpServiceBean publishedObjectCleanUpService;
    @MockBean(name = CA_CLEAN_UP_SERVICE)
    private CaCleanUpServiceBean caCleanUpService;

    @MockBean(name = PUBLISHER_SYNC_SERVICE)
    private PublisherSyncService publisherSyncService;

    private static final String hostname = getHostName();

    @Before
    public void setUp() {
        when(repositoryConfiguration.getLocalRepositoryDirectory()).thenReturn(new File("/tmp"));
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(URI.create("rsync://localhost:873/repo"));

        when(activeNodeService.getActiveNodeName()).thenReturn(hostname);

        when(manifestCrlUpdateService.isActive()).thenReturn(true);
        when(allCertificateUpdateService.isActive()).thenReturn(true);
        when(publicRepositoryPublicationService.isActive()).thenReturn(true);
        when(publicRepositoryRsyncService.isActive()).thenReturn(true);
        when(publicRepositoryRrdpService.isActive()).thenReturn(true);
        when(productionCaKeyRolloverManagementService.isActive()).thenReturn(true);
        when(hostedCaKeyRolloverManagementService.isActive()).thenReturn(true);
        when(keyPairActivationManagementService.isActive()).thenReturn(true);
        when(keyPairRevocationManagementService.isActive()).thenReturn(true);
        when(certificateExpirationService.isActive()).thenReturn(true);
        when(risWhoisUpdateService.isActive()).thenReturn(true);
        when(roaAlertBackgroundServiceDaily.isActive()).thenReturn(true);
        when(resourceCacheUpdateService.isActive()).thenReturn(true);
        when(publishedObjectCleanUpService.isActive()).thenReturn(true);
        when(caCleanUpService.isActive()).thenReturn(true);
        when(publisherSyncService.isActive()).thenReturn(true);

        when(manifestCrlUpdateService.isWaitingOrRunning()).thenReturn(false);
        when(allCertificateUpdateService.isWaitingOrRunning()).thenReturn(false);
        when(publicRepositoryPublicationService.isWaitingOrRunning()).thenReturn(false);
        when(publicRepositoryRsyncService.isWaitingOrRunning()).thenReturn(false);
        when(publicRepositoryRrdpService.isWaitingOrRunning()).thenReturn(false);
        when(productionCaKeyRolloverManagementService.isWaitingOrRunning()).thenReturn(false);
        when(hostedCaKeyRolloverManagementService.isWaitingOrRunning()).thenReturn(false);
        when(keyPairActivationManagementService.isWaitingOrRunning()).thenReturn(false);
        when(keyPairRevocationManagementService.isWaitingOrRunning()).thenReturn(false);
        when(certificateExpirationService.isWaitingOrRunning()).thenReturn(false);
        when(risWhoisUpdateService.isWaitingOrRunning()).thenReturn(false);
        when(roaAlertBackgroundServiceDaily.isWaitingOrRunning()).thenReturn(false);
        when(resourceCacheUpdateService.isWaitingOrRunning()).thenReturn(false);
        when(publishedObjectCleanUpService.isWaitingOrRunning()).thenReturn(false);
        when(caCleanUpService.isWaitingOrRunning()).thenReturn(false);
        when(publisherSyncService.isWaitingOrRunning()).thenReturn(false);

        when(manifestCrlUpdateService.getStatus()).thenReturn("not running");
        when(allCertificateUpdateService.getStatus()).thenReturn("not running");
        when(publicRepositoryPublicationService.getStatus()).thenReturn("not running");
        when(publicRepositoryRsyncService.getStatus()).thenReturn("not running");
        when(publicRepositoryRrdpService.getStatus()).thenReturn("not running");
        when(productionCaKeyRolloverManagementService.getStatus()).thenReturn("not running");
        when(hostedCaKeyRolloverManagementService.getStatus()).thenReturn("not running");
        when(keyPairActivationManagementService.getStatus()).thenReturn("not running");
        when(keyPairRevocationManagementService.getStatus()).thenReturn("not running");
        when(certificateExpirationService.getStatus()).thenReturn("not running");
        when(risWhoisUpdateService.getStatus()).thenReturn("not running");
        when(roaAlertBackgroundServiceDaily.getStatus()).thenReturn("not running");
        when(resourceCacheUpdateService.getStatus()).thenReturn("not running");
        when(publishedObjectCleanUpService.getStatus()).thenReturn("not running");
        when(caCleanUpService.getStatus()).thenReturn("not running");
        when(publisherSyncService.getStatus()).thenReturn("not running");
    }

    @Test
    public void shouldRender() {
        tester.startPage(SystemStatusPage.class);

        tester.assertRenderedPage(SystemStatusPage.class);
    }

    @Test
    public void shouldUpdateActiveNode() {
        activeNodeService.setActiveNodeName("foo.ripe.net");

        tester.startPage(SystemStatusPage.class);

        FormTester formTester = tester.newFormTester("activeNodeForm");
        formTester.setValue("activeNode", "foo.ripe.net");
        formTester.submit();

        tester.assertRenderedPage(SystemStatusPage.class);
    }

    @Test
    public void shouldUpdateCrlsAndManifests() {
        assertThatServiceIsTriggered("updateManifestLink", manifestCrlUpdateService, MANIFEST_CRL_UPDATE_SERVICE);
    }

    @Test
    public void shouldUpdatePublishedObjects() {
        assertThatServiceIsTriggered("updatePublicationStatusLink", publicRepositoryPublicationService, PUBLIC_REPOSITORY_PUBLICATION_SERVICE);
    }

    @Test
    public void shouldUpdateRsyncRepository() {
        assertThatServiceIsTriggered("updateRsyncLink", publicRepositoryRsyncService, PUBLIC_REPOSITORY_RSYNC_SERVICE);
    }

    @Test
    public void shouldUpdateRrdpRepository() {
        assertThatServiceIsTriggered("updateRrdpLink", publicRepositoryRrdpService, PUBLIC_REPOSITORY_RRDP_SERVICE);
    }

    @Test
    public void shouldActivatePendingKeyPairs() {
        assertThatServiceIsTriggered("activatePendingKeyPairsLink", keyPairActivationManagementService, KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE);
    }

    @Test
    public void shouldRollOverMemberKeyPairs() {
        assertThatServiceIsTriggered("hostedCaRollOverLink", hostedCaKeyRolloverManagementService, HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE);
    }

    @Test
    public void shouldRollOverProductionCaKeyPairs() {
        assertThatServiceIsTriggered("productionCaRollOverLink", productionCaKeyRolloverManagementService, PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE);
    }

    @Test
    public void shouldUpdateAllIncomingCertificates() {
        assertThatServiceIsTriggered("updateResourcesLink", allCertificateUpdateService, ALL_CA_CERTIFICATE_UPDATE_SERVICE);
    }

    private void assertThatServiceIsTriggered(String link, BackgroundService backgroundService, String backgroundServiceName) {
        when(backgroundService.getName()).thenReturn("name");

        tester.startPage(SystemStatusPage.class);
        tester.clickLink(link);
        tester.assertRenderedPage(SystemStatusPage.class);

        verify(backgroundServices).trigger(backgroundServiceName);
    }

    private static String getHostName() {
        String hostname = "Unknown";
        try {
            InetAddress addr = InetAddress.getLocalHost();
            hostname = addr.getHostName();
        } catch (UnknownHostException e) {
        }
        return hostname;
    }
}
