package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import net.ripe.rpki.services.impl.background.CaCleanUpServiceBean;
import net.ripe.rpki.services.impl.background.CertificateExpirationServiceBean;
import net.ripe.rpki.services.impl.background.KeyPairActivationManagementServiceBean;
import net.ripe.rpki.services.impl.background.KeyPairRevocationManagementServiceBean;
import net.ripe.rpki.services.impl.background.ManifestCrlUpdateServiceBean;
import net.ripe.rpki.services.impl.background.HostedCaKeyRolloverManagementServiceBean;
import net.ripe.rpki.services.impl.background.ProductionCaKeyRolloverManagementServiceBean;
import net.ripe.rpki.services.impl.background.PublicRepositoryPublicationServiceBean;
import net.ripe.rpki.services.impl.background.PublicRepositoryRrdpServiceBean;
import net.ripe.rpki.services.impl.background.PublicRepositoryRsyncServiceBean;
import net.ripe.rpki.services.impl.background.PublishedObjectCleanUpServiceBean;
import net.ripe.rpki.services.impl.background.PublisherSyncService;
import net.ripe.rpki.services.impl.background.ResourceCacheUpdateServiceBean;
import net.ripe.rpki.services.impl.background.RisWhoisUpdateServiceBean;
import net.ripe.rpki.services.impl.background.RoaAlertBackgroundServiceDailyBean;
import net.ripe.rpki.ui.application.CertificationWicketTestCase;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static net.ripe.rpki.services.impl.background.BackgroundServices.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SystemStatusPageTest extends CertificationWicketTestCase {

    private BackgroundServices backgroundServices;
    private BackgroundService manifestCrlUpdateService;
    private BackgroundService publicRepositoryPublicationService;
    private BackgroundService publicRepositoryRsyncService;
    private BackgroundService publicRepositoryRrdpService;
    private BackgroundService productionCaKeyRolloverManagementService;
    private BackgroundService hostedCaKeyRolloverManagementService;
    private BackgroundService keyPairActivationManagementService;
    private BackgroundService keyPairRevocationManagementService;
    private BackgroundService certificateExpirationService;
    private BackgroundService risWhoisUpdateService;
    private BackgroundService roaAlertBackgroundServiceDaily;
    private BackgroundService resourceCacheUpdateService;
    private BackgroundService publishedObjectCleanUpService;
    private BackgroundService caCleanUpService;

    private BackgroundService publisherSyncService;

    private static final String hostname = getHostName();

    @Before
    public void setUp() {
        backgroundServices = mock(BackgroundServices.class);
        addBeanToContext("backgroundServices", backgroundServices);

        manifestCrlUpdateService = mock(ManifestCrlUpdateServiceBean.class);
        addBeanToContext(MANIFEST_CRL_UPDATE_SERVICE, manifestCrlUpdateService);

        publicRepositoryPublicationService = mock(PublicRepositoryPublicationServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_PUBLICATION_SERVICE, publicRepositoryPublicationService);

        publicRepositoryRsyncService = mock(PublicRepositoryRsyncServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_RSYNC_SERVICE, publicRepositoryRsyncService);

        publicRepositoryRrdpService = mock(PublicRepositoryRrdpServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_RRDP_SERVICE, publicRepositoryRrdpService);

        productionCaKeyRolloverManagementService = mock(ProductionCaKeyRolloverManagementServiceBean.class);
        addBeanToContext(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE, productionCaKeyRolloverManagementService);

        hostedCaKeyRolloverManagementService = mock(HostedCaKeyRolloverManagementServiceBean.class);
        addBeanToContext(HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE, hostedCaKeyRolloverManagementService);

        keyPairActivationManagementService = mock(KeyPairActivationManagementServiceBean.class);
        addBeanToContext(KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE, keyPairActivationManagementService);

        keyPairRevocationManagementService = mock(KeyPairRevocationManagementServiceBean.class);
        addBeanToContext(KEY_PAIR_REVOCATION_MANAGEMENT_SERVICE, keyPairRevocationManagementService);

        certificateExpirationService = mock(CertificateExpirationServiceBean.class);
        addBeanToContext(CERTIFICATE_EXPIRATION_SERVICE, certificateExpirationService);

        risWhoisUpdateService = mock(RisWhoisUpdateServiceBean.class);
        addBeanToContext(RIS_WHOIS_UPDATE_SERVICE, risWhoisUpdateService);

        roaAlertBackgroundServiceDaily = mock(RoaAlertBackgroundServiceDailyBean.class);
        addBeanToContext(ROA_ALERT_BACKGROUND_SERVICE, roaAlertBackgroundServiceDaily);

        resourceCacheUpdateService = mock(ResourceCacheUpdateServiceBean.class);
        addBeanToContext(RESOURCE_CACHE_UPDATE_SERVICE, resourceCacheUpdateService);

        publishedObjectCleanUpService = mock(PublishedObjectCleanUpServiceBean.class);
        addBeanToContext(PUBLISHED_OBJECT_CLEAN_UP_SERVICE, publishedObjectCleanUpService);

        caCleanUpService = mock(CaCleanUpServiceBean.class);
        addBeanToContext(CA_CLEAN_UP_SERVICE, caCleanUpService);

        publisherSyncService = mock(PublisherSyncService.class);
        addBeanToContext(PUBLISHER_SYNC_SERVICE, publisherSyncService);

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
