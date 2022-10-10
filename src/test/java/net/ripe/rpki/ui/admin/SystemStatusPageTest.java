package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import net.ripe.rpki.services.impl.background.CaCleanUpServiceBean;
import net.ripe.rpki.services.impl.background.CertificateExpirationServiceBean;
import net.ripe.rpki.services.impl.background.KeyPairActivationManagementServiceBean;
import net.ripe.rpki.services.impl.background.KeyPairRevocationManagementServiceBean;
import net.ripe.rpki.services.impl.background.ManifestCrlUpdateServiceBean;
import net.ripe.rpki.services.impl.background.MemberKeyRolloverManagementServiceBean;
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
import static org.easymock.EasyMock.*;

public class SystemStatusPageTest extends CertificationWicketTestCase {

    private BackgroundServices backgroundServices;
    private BackgroundService manifestCrlUpdateService;
    private BackgroundService publicRepositoryPublicationService;
    private BackgroundService publicRepositoryRsyncService;
    private BackgroundService publicRepositoryRrdpService;
    private BackgroundService productionCaKeyRolloverManagementService;
    private BackgroundService memberKeyRolloverManagementService;
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
        backgroundServices = createMock(BackgroundServices.class);
        addBeanToContext("backgroundServices", backgroundServices);

        manifestCrlUpdateService = createMock(ManifestCrlUpdateServiceBean.class);
        addBeanToContext(MANIFEST_CRL_UPDATE_SERVICE, manifestCrlUpdateService);

        publicRepositoryPublicationService = createMock(PublicRepositoryPublicationServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_PUBLICATION_SERVICE, publicRepositoryPublicationService);

        publicRepositoryRsyncService = createMock(PublicRepositoryRsyncServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_RSYNC_SERVICE, publicRepositoryRsyncService);

        publicRepositoryRrdpService = createMock(PublicRepositoryRrdpServiceBean.class);
        addBeanToContext(PUBLIC_REPOSITORY_RRDP_SERVICE, publicRepositoryRrdpService);

        productionCaKeyRolloverManagementService = createMock(ProductionCaKeyRolloverManagementServiceBean.class);
        addBeanToContext(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE, productionCaKeyRolloverManagementService);

        memberKeyRolloverManagementService = createMock(MemberKeyRolloverManagementServiceBean.class);
        addBeanToContext(MEMBER_KEY_ROLLOVER_MANAGEMENT_SERVICE, memberKeyRolloverManagementService);

        keyPairActivationManagementService = createMock(KeyPairActivationManagementServiceBean.class);
        addBeanToContext(KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE, keyPairActivationManagementService);

        keyPairRevocationManagementService = createMock(KeyPairRevocationManagementServiceBean.class);
        addBeanToContext(KEY_PAIR_REVOCATION_MANAGEMENT_SERVICE, keyPairRevocationManagementService);

        certificateExpirationService = createMock(CertificateExpirationServiceBean.class);
        addBeanToContext(CERTIFICATE_EXPIRATION_SERVICE, certificateExpirationService);

        risWhoisUpdateService = createMock(RisWhoisUpdateServiceBean.class);
        addBeanToContext(RIS_WHOIS_UPDATE_SERVICE, risWhoisUpdateService);

        roaAlertBackgroundServiceDaily = createMock(RoaAlertBackgroundServiceDailyBean.class);
        addBeanToContext(ROA_ALERT_BACKGROUND_SERVICE, roaAlertBackgroundServiceDaily);

        resourceCacheUpdateService = createMock(ResourceCacheUpdateServiceBean.class);
        addBeanToContext(RESOURCE_CACHE_UPDATE_SERVICE, resourceCacheUpdateService);

        publishedObjectCleanUpService = createMock(PublishedObjectCleanUpServiceBean.class);
        addBeanToContext(PUBLISHED_OBJECT_CLEAN_UP_SERVICE, publishedObjectCleanUpService);

        caCleanUpService = createMock(CaCleanUpServiceBean.class);
        addBeanToContext(CA_CLEAN_UP_SERVICE, caCleanUpService);

        publisherSyncService = createMock(PublisherSyncService.class);
        addBeanToContext(PUBLISHER_SYNC_SERVICE, publisherSyncService);

        expect(repositoryConfiguration.getLocalRepositoryDirectory()).andReturn(new File("/tmp")).anyTimes();
        expect(repositoryConfiguration.getPublicRepositoryUri()).andReturn(URI.create("rsync://localhost:873/repo")).anyTimes();

        expect(activeNodeService.getActiveNodeName()).andReturn(hostname).anyTimes();

        expect(manifestCrlUpdateService.isActive()).andReturn(true).anyTimes();
        expect(allCertificateUpdateService.isActive()).andReturn(true).anyTimes();
        expect(publicRepositoryPublicationService.isActive()).andReturn(true).anyTimes();
        expect(publicRepositoryRsyncService.isActive()).andReturn(true).anyTimes();
        expect(publicRepositoryRrdpService.isActive()).andReturn(true).anyTimes();
        expect(productionCaKeyRolloverManagementService.isActive()).andReturn(true).anyTimes();
        expect(memberKeyRolloverManagementService.isActive()).andReturn(true).anyTimes();
        expect(keyPairActivationManagementService.isActive()).andReturn(true).anyTimes();
        expect(keyPairRevocationManagementService.isActive()).andReturn(true).anyTimes();
        expect(certificateExpirationService.isActive()).andReturn(true).anyTimes();
        expect(risWhoisUpdateService.isActive()).andReturn(true).anyTimes();
        expect(roaAlertBackgroundServiceDaily.isActive()).andReturn(true).anyTimes();
        expect(resourceCacheUpdateService.isActive()).andReturn(true).anyTimes();
        expect(publishedObjectCleanUpService.isActive()).andReturn(true).anyTimes();
        expect(caCleanUpService.isActive()).andReturn(true).anyTimes();
        expect(publisherSyncService.isActive()).andReturn(true).anyTimes();

        expect(manifestCrlUpdateService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(allCertificateUpdateService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryPublicationService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryRsyncService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryRrdpService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(productionCaKeyRolloverManagementService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(memberKeyRolloverManagementService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(keyPairActivationManagementService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(keyPairRevocationManagementService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(certificateExpirationService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(risWhoisUpdateService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(roaAlertBackgroundServiceDaily.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(resourceCacheUpdateService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(publishedObjectCleanUpService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(caCleanUpService.isWaitingOrRunning()).andReturn(false).anyTimes();
        expect(publisherSyncService.isWaitingOrRunning()).andReturn(false).anyTimes();

        expect(manifestCrlUpdateService.getStatus()).andReturn("not running").anyTimes();
        expect(allCertificateUpdateService.getStatus()).andReturn("not running").anyTimes();
        expect(publicRepositoryPublicationService.getStatus()).andReturn("not running").anyTimes();
        expect(publicRepositoryRsyncService.getStatus()).andReturn("not running").anyTimes();
        expect(publicRepositoryRrdpService.getStatus()).andReturn("not running").anyTimes();
        expect(productionCaKeyRolloverManagementService.getStatus()).andReturn("not running").anyTimes();
        expect(memberKeyRolloverManagementService.getStatus()).andReturn("not running").anyTimes();
        expect(keyPairActivationManagementService.getStatus()).andReturn("not running").anyTimes();
        expect(keyPairRevocationManagementService.getStatus()).andReturn("not running").anyTimes();
        expect(certificateExpirationService.getStatus()).andReturn("not running").anyTimes();
        expect(risWhoisUpdateService.getStatus()).andReturn("not running").anyTimes();
        expect(roaAlertBackgroundServiceDaily.getStatus()).andReturn("not running").anyTimes();
        expect(resourceCacheUpdateService.getStatus()).andReturn("not running").anyTimes();
        expect(publishedObjectCleanUpService.getStatus()).andReturn("not running").anyTimes();
        expect(caCleanUpService.getStatus()).andReturn("not running").anyTimes();
        expect(publisherSyncService.getStatus()).andReturn("not running").anyTimes();
    }

    @Test
    public void shouldRender() {
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateActiveNode() {
        activeNodeService.setActiveNodeName("foo.ripe.net");
        replayMocks();

        tester.startPage(SystemStatusPage.class);

        FormTester formTester = tester.newFormTester("activeNodeForm");
        formTester.setValue("activeNode", "foo.ripe.net");
        formTester.submit();
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateCrlsAndManifests() {
        backgroundServices.trigger(MANIFEST_CRL_UPDATE_SERVICE); expectLastCall();
        expect(manifestCrlUpdateService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateManifestLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdatePublishedObjects() {
        backgroundServices.trigger(PUBLIC_REPOSITORY_PUBLICATION_SERVICE); expectLastCall();
        expect(publicRepositoryPublicationService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updatePublicationStatusLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateRsyncRepository() {
        backgroundServices.trigger(PUBLIC_REPOSITORY_RSYNC_SERVICE); expectLastCall();
        expect(publicRepositoryRsyncService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateRsyncLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateRrdpRepository() {
        backgroundServices.trigger(PUBLIC_REPOSITORY_RRDP_SERVICE); expectLastCall();
        expect(publicRepositoryRrdpService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateRrdpLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldActivatePendingKeyPairs() {
        backgroundServices.trigger(KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE); expectLastCall();
        expect(keyPairActivationManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("activatePendingKeyPairsLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldRollOverMemberKeyPairs() {
        backgroundServices.trigger(MEMBER_KEY_ROLLOVER_MANAGEMENT_SERVICE); expectLastCall();
        expect(memberKeyRolloverManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("memberRollOverLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldRollOverProductionCaKeyPairs() {
        backgroundServices.trigger(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE); expectLastCall();
        expect(productionCaKeyRolloverManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("productionCaRollOverLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateAllIncomingCertificates() {
        backgroundServices.trigger(ALL_CA_CERTIFICATE_UPDATE_SERVICE); expectLastCall();
        expect(allCertificateUpdateService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateResourcesLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Override
    protected void replayMocks() {
        super.replayMocks();
        replay(backgroundServices);
        replay(manifestCrlUpdateService);
        replay(publicRepositoryPublicationService);
        replay(publicRepositoryRsyncService);
        replay(publicRepositoryRrdpService);
        replay(productionCaKeyRolloverManagementService);
        replay(memberKeyRolloverManagementService);
        replay(keyPairActivationManagementService);
        replay(keyPairRevocationManagementService);
        replay(certificateExpirationService);
        replay(risWhoisUpdateService);
        replay(roaAlertBackgroundServiceDaily);
        replay(resourceCacheUpdateService);
        replay(publishedObjectCleanUpService);
        replay(caCleanUpService);
        replay(publisherSyncService);
    }

    @Override
    protected void verifyMocks() {
        super.verifyMocks();
        verify(backgroundServices);
        verify(manifestCrlUpdateService);
        verify(publicRepositoryPublicationService);
        verify(publicRepositoryRsyncService);
        verify(publicRepositoryRrdpService);
        verify(productionCaKeyRolloverManagementService);
        verify(memberKeyRolloverManagementService);
        verify(keyPairActivationManagementService);
        verify(keyPairRevocationManagementService);
        verify(certificateExpirationService);
        verify(risWhoisUpdateService);
        verify(roaAlertBackgroundServiceDaily);
        verify(resourceCacheUpdateService);
        verify(publishedObjectCleanUpService);
        verify(caCleanUpService);
        verify(publisherSyncService);
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
