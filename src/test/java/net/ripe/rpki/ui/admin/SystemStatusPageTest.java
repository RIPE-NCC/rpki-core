package net.ripe.rpki.ui.admin;

import net.ripe.rpki.core.services.background.BackgroundServiceTimings;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.ui.application.CertificationWicketTestCase;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_PUBLICATION_SERVICE;
import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RRDP_SERVICE;
import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_RSYNC_SERVICE;
import static org.easymock.EasyMock.*;

public class SystemStatusPageTest extends CertificationWicketTestCase {

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

    private static final String hostname = getHostName();

    @Before
    public void setUp() {
        manifestCrlUpdateService = createMock(BackgroundService.class);
        addBeanToContext("manifestCrlUpdateService", manifestCrlUpdateService);

        publicRepositoryPublicationService = createMock(BackgroundService.class);
        addBeanToContext(PUBLIC_REPOSITORY_PUBLICATION_SERVICE, publicRepositoryPublicationService);

        publicRepositoryRsyncService = createMock(BackgroundService.class);
        addBeanToContext(PUBLIC_REPOSITORY_RSYNC_SERVICE, publicRepositoryRsyncService);

        publicRepositoryRrdpService = createMock(BackgroundService.class);
        addBeanToContext(PUBLIC_REPOSITORY_RRDP_SERVICE, publicRepositoryRrdpService);

        productionCaKeyRolloverManagementService = createMock(BackgroundService.class);
        addBeanToContext("productionCaKeyRolloverManagementService", productionCaKeyRolloverManagementService);

        memberKeyRolloverManagementService = createMock(BackgroundService.class);
        addBeanToContext("memberKeyRolloverManagementService", memberKeyRolloverManagementService);

        keyPairActivationManagementService = createMock(BackgroundService.class);
        addBeanToContext("keyPairActivationManagementService", keyPairActivationManagementService);

        keyPairRevocationManagementService = createMock(BackgroundService.class);
        addBeanToContext("keyPairRevocationManagementService", keyPairRevocationManagementService);

        certificateExpirationService = createMock(BackgroundService.class);
        addBeanToContext("certificateExpirationService", certificateExpirationService);

        risWhoisUpdateService = createMock(BackgroundService.class);
        addBeanToContext("risWhoisUpdateService", risWhoisUpdateService);

        roaAlertBackgroundServiceDaily = createMock(BackgroundService.class);
        addBeanToContext("roaAlertBackgroundServiceDaily", roaAlertBackgroundServiceDaily);

        resourceCacheUpdateService = createMock(BackgroundService.class);
        addBeanToContext("resourceCacheUpdateService", resourceCacheUpdateService);

        publishedObjectCleanUpService = createMock(BackgroundService.class);
        addBeanToContext("publishedObjectCleanUpService", publishedObjectCleanUpService);

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

        expect(manifestCrlUpdateService.isRunning()).andReturn(false).anyTimes();
        expect(allCertificateUpdateService.isRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryPublicationService.isRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryRsyncService.isRunning()).andReturn(false).anyTimes();
        expect(publicRepositoryRrdpService.isRunning()).andReturn(false).anyTimes();
        expect(productionCaKeyRolloverManagementService.isRunning()).andReturn(false).anyTimes();
        expect(memberKeyRolloverManagementService.isRunning()).andReturn(false).anyTimes();
        expect(keyPairActivationManagementService.isRunning()).andReturn(false).anyTimes();
        expect(keyPairRevocationManagementService.isRunning()).andReturn(false).anyTimes();
        expect(certificateExpirationService.isRunning()).andReturn(false).anyTimes();
        expect(risWhoisUpdateService.isRunning()).andReturn(false).anyTimes();
        expect(roaAlertBackgroundServiceDaily.isRunning()).andReturn(false).anyTimes();
        expect(resourceCacheUpdateService.isRunning()).andReturn(false).anyTimes();
        expect(publishedObjectCleanUpService.isRunning()).andReturn(false).anyTimes();

        expect(manifestCrlUpdateService.isBlocked()).andReturn(false).anyTimes();
        expect(publicRepositoryPublicationService.isBlocked()).andReturn(false).anyTimes();
        expect(publicRepositoryRsyncService.isBlocked()).andReturn(false).anyTimes();
        expect(publicRepositoryRrdpService.isBlocked()).andReturn(false).anyTimes();
        expect(allCertificateUpdateService.isBlocked()).andReturn(false).anyTimes();
        expect(productionCaKeyRolloverManagementService.isBlocked()).andReturn(false).anyTimes();
        expect(memberKeyRolloverManagementService.isBlocked()).andReturn(false).anyTimes();
        expect(keyPairActivationManagementService.isBlocked()).andReturn(false).anyTimes();
        expect(keyPairRevocationManagementService.isBlocked()).andReturn(false).anyTimes();
        expect(certificateExpirationService.isBlocked()).andReturn(false).anyTimes();
        expect(risWhoisUpdateService.isBlocked()).andReturn(false).anyTimes();
        expect(roaAlertBackgroundServiceDaily.isBlocked()).andReturn(false).anyTimes();
        expect(resourceCacheUpdateService.isBlocked()).andReturn(false).anyTimes();
        expect(publishedObjectCleanUpService.isBlocked()).andReturn(false).anyTimes();

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
        expect(manifestCrlUpdateService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(manifestCrlUpdateService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateManifestLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdatePublishedObjects() {
        expect(publicRepositoryPublicationService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(publicRepositoryPublicationService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updatePublicationStatusLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateRsyncRepository() {
        expect(publicRepositoryRsyncService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(publicRepositoryRsyncService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateRsyncLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateRrdpRepository() {
        expect(publicRepositoryRrdpService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(publicRepositoryRrdpService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("updateRrdpLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldActivatePendingKeyPairs() {
        expect(keyPairActivationManagementService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(keyPairActivationManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("activatePendingKeyPairsLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldRollOverMemberKeyPairs() {
        expect(memberKeyRolloverManagementService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(memberKeyRolloverManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("memberRollOverLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldRollOverProductionCaKeyPairs() {
        expect(productionCaKeyRolloverManagementService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
        expect(productionCaKeyRolloverManagementService.getName()).andReturn("name");
        replayMocks();

        tester.startPage(SystemStatusPage.class);
        tester.clickLink("productionCaRollOverLink");
        tester.assertRenderedPage(SystemStatusPage.class);

        verifyMocks();
    }

    @Test
    public void shouldUpdateResources() {
        expect(allCertificateUpdateService.execute()).andReturn(new BackgroundServiceTimings(1, 1));
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
    }

    @Override
    protected void verifyMocks() {
        super.verifyMocks();
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
