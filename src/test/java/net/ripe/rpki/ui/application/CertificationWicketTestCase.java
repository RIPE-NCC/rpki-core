package net.ripe.rpki.ui.application;


import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.ripencc.services.impl.RipeNccInternalNamePresenter;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.*;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import net.ripe.rpki.ui.configuration.UiConfiguration;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.test.ApplicationContextMock;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Before;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ALL_RESOURCES;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ROOT;
import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class CertificationWicketTestCase {

    protected WicketTester tester;

    protected static final X500Principal ALL_RESOURCES_CA_NAME = new X500Principal("CN=All Resources CA,O=RIPE NCC,C=NL");
    protected static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    protected static final long PRODUCTION_CA_ID = 42L;
    protected static final VersionedId PRODUCTION_CA_VERSIONED_ID = new VersionedId(PRODUCTION_CA_ID, 3);

    protected static final CertificateAuthorityData ALL_RESOURCES_CA_DATA = new ManagedCertificateAuthorityData(
        new VersionedId(31L, 2), ALL_RESOURCES_CA_NAME, UUID.randomUUID(), null, ALL_RESOURCES,
        ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());
    protected static final CertificateAuthorityData PRODUCTION_CA_DATA = new ManagedCertificateAuthorityData(
        PRODUCTION_CA_VERSIONED_ID, PRODUCTION_CA_NAME, UUID.randomUUID(), ALL_RESOURCES_CA_DATA.getId(), ROOT,
        ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    protected static final VersionedId MEMBER_CA_VERSIONED_ID = new VersionedId(PRODUCTION_CA_ID, 3);

    // Mocks
    protected CertificateAuthorityViewService caViewService;
    protected ResourceCertificateViewService certViewService;
    protected RoaViewService roaService;
    protected UiConfiguration uiConfiguration;
    protected RepositoryConfiguration repositoryConfiguration;
    protected ApplicationContextMock applicationContextMock;
    protected RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    protected BgpRisEntryViewService bgpRisEntryRepository;
    protected CommandService commandService;
    protected InternalNamePresenter statsCollectorNames;
    protected BackgroundService allCertificateUpdateService;
    protected ActiveNodeService activeNodeService;


    @Before
    public final void setUpMockCertificationService() {
        System.setProperty("disable.development.user", "true");

        // Clean up the current threadlocal variables
        AuthenticatedWebSession.unset();

        caViewService = mock(CertificateAuthorityViewService.class);
        certViewService = mock(ResourceCertificateViewService.class);
        roaService = mock(RoaViewService.class);
        uiConfiguration = mock(UiConfiguration.class);
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        roaAlertConfigurationViewService = mock(RoaAlertConfigurationViewService.class);
        bgpRisEntryRepository = mock(BgpRisEntryViewService.class);
        commandService = mock(CommandService.class);
        statsCollectorNames = mock(RipeNccInternalNamePresenter.class);
        allCertificateUpdateService = mock(AllCaCertificateUpdateServiceBean.class);
        activeNodeService = mock(ActiveNodeService.class);

        when(activeNodeService.getCurrentNodeName()).thenReturn("current-node");
        when(uiConfiguration.getDeploymentEnvironmentBannerImage()).thenReturn("");
        when(uiConfiguration.showEnvironmentBanner()).thenReturn(true);
        when(uiConfiguration.showEnvironmentStripe()).thenReturn(false);
        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(ALL_RESOURCES_CA_NAME);
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(PRODUCTION_CA_NAME);
        when(caViewService.findCertificateAuthorityByName(ALL_RESOURCES_CA_NAME)).thenReturn(ALL_RESOURCES_CA_DATA);
        when(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_DATA);

        applicationContextMock = new ApplicationContextMock();
        applicationContextMock.putBean("caViewService", caViewService);
        applicationContextMock.putBean("certViewService", certViewService);
        applicationContextMock.putBean("roaService", roaService);
        applicationContextMock.putBean("uiConfiguration", uiConfiguration);
        applicationContextMock.putBean("sharedConfiguration", repositoryConfiguration);
        applicationContextMock.putBean("alertSubscriptionService", roaAlertConfigurationViewService);
        applicationContextMock.putBean("bgpRisEntryRepository", bgpRisEntryRepository);
        applicationContextMock.putBean("commandService", commandService);
        applicationContextMock.putBean("userPrincipalResolver", statsCollectorNames);
        applicationContextMock.putBean(ALL_CA_CERTIFICATE_UPDATE_SERVICE, allCertificateUpdateService);
        applicationContextMock.putBean("activeNodeService", activeNodeService);

        tester = new WicketTester(getApplicationStub());
    }

    protected WebApplication getApplicationStub() {
        return new CertificationAdminWicketApplicationStub(applicationContextMock);
    }

    @After
    public final void cleanUp() {
        System.clearProperty("disable.development.user");
    }

    protected void addBeanToContext(String name, Object bean) {
        applicationContextMock.putBean(name, bean);
    }
}
