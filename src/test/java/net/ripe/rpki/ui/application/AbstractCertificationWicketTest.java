package net.ripe.rpki.ui.application;


import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.ripencc.services.impl.RipeNccInternalNamePresenter;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceClassMap;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.ui.configuration.UiConfiguration;
import org.apache.wicket.authentication.AuthenticatedWebApplication;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.spring.test.ApplicationContextMock;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Before;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ROOT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class AbstractCertificationWicketTest {

    protected WicketTester tester;

    protected static final X500Principal PRODUCTION_CA_PRINCIPAL = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    protected static final long PRODUCTION_CA_ID = 42l;
    protected static final VersionedId PRODUCTION_CA_VERSIONED_ID = new VersionedId(PRODUCTION_CA_ID, 3);

    protected static final IpResourceSet RESOURCES = new IpResourceSet();
    static {
        RESOURCES.addAll(IpResourceSet.ALL_PRIVATE_USE_RESOURCES);
        RESOURCES.addAll(IpResourceSet.parse("2.0.0.0/8"));
        RESOURCES.addAll(IpResourceSet.parse("3.0.0.0/8"));
        RESOURCES.addAll(IpResourceSet.parse("4.0.0.0/8"));
        RESOURCES.addAll(IpResourceSet.parse("5.0.0.0/8"));
    }

    protected static final CertificateAuthorityData PRODUCTION_CA_DATA = new HostedCertificateAuthorityData(
        PRODUCTION_CA_VERSIONED_ID, PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), null, ROOT, RESOURCES, Collections.emptyList());


    protected static final X500Principal ALL_RESOURCES_CA_PRINCIPAL = new X500Principal("CN=ALL Resources,O=RIPE NCC,C=NL");

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
    protected ResourceLookupService resourceLookupService;
    protected CertificateAuthorityCreateService certificateAuthorityCreateService;


    @Before
    public final void setUpMockCertificationService() {
        System.setProperty("disable.development.user", "true");
        AuthenticatedWebSession.unset(); // Clean up the current threadlocal variables
        caViewService = mock(CertificateAuthorityViewService.class);
        certViewService = mock(ResourceCertificateViewService.class);
        roaService = mock(RoaViewService.class);
        uiConfiguration = mock(UiConfiguration.class);
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        roaAlertConfigurationViewService = mock(RoaAlertConfigurationViewService.class);
        bgpRisEntryRepository = mock(BgpRisEntryViewService.class);
        commandService = mock(CommandService.class);
        statsCollectorNames = mock(RipeNccInternalNamePresenter.class);
        allCertificateUpdateService = mock(BackgroundService.class);
        activeNodeService = mock(ActiveNodeService.class);
        resourceLookupService = mock(ResourceLookupService.class);
        certificateAuthorityCreateService = mock(CertificateAuthorityCreateService.class);

        IpResourceSet resources = new IpResourceSet(IpResourceSet.parse("2.0.0.0/8, 5.0.0.0/8, 37.0.0.0/8, 46.0.0.0/8, 62.0.0.0/8, 77.0.0.0-95.255.255.255, 109.0.0.0/8, 141.0.0.0/8, 145.0.0.0/8, 151.0.0.0/8, 178.0.0.0/8, 188.0.0.0/8, 193.0.0.0-195.255.255.255, 196.200.0.0/13, 212.0.0.0/7, 217.0.0.0/8, 2001:600::-2001:bff:ffff:ffff:ffff:ffff:ffff:ffff, 2001:1400::/22, 2001:1a00::-2001:3bff:ffff:ffff:ffff:ffff:ffff:ffff, 2001:4000::/23, 2001:4600::/23, 2001:4a00::-2001:4dff:ffff:ffff:ffff:ffff:ffff:ffff, 2001:5000::/20, 2003::/18, 2a00::/12"));
        resources.addAll(IpResourceSet.parse("3.0.0.0/24, 4.0.0.0/24"));
        resources.addAll(IpResourceSet.parse("6.0.0.0/24"));
        resources.addAll(IpResourceSet.parse("7.0.0.0/16"));
        resources.addAll(IpResourceSet.parse("8.0.0.0/24, 9.0.0.0/24"));

        given(resourceLookupService.lookupProductionCaResources()).willReturn(resources);
        given(activeNodeService.getCurrentNodeName()).willReturn("current-node");
        given(uiConfiguration.getDeploymentEnvironmentBannerImage()).willReturn("");

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
        applicationContextMock.putBean("allCertificateUpdateService", allCertificateUpdateService);
        applicationContextMock.putBean("activeNodeService", activeNodeService);
        applicationContextMock.putBean("resourceLookupService", resourceLookupService);
        applicationContextMock.putBean("certificateAuthorityCreateService", certificateAuthorityCreateService);

        tester = new WicketTester(getApplicationStub());
    }

    protected AuthenticatedWebApplication getApplicationStub() {
        return new CertificationAdminWicketApplicationStub(applicationContextMock);
    }

    @After
    public final void cleanUp() {
        System.clearProperty("disable.development.user");
    }

    protected void setUpMocksForCurrentCAInitialization() {
        given(repositoryConfiguration.getProductionCaPrincipal()).willReturn(PRODUCTION_CA_PRINCIPAL);
        given(repositoryConfiguration.getAllResourcesCaPrincipal()).willReturn(ALL_RESOURCES_CA_PRINCIPAL);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(PRODUCTION_CA_DATA);
        given(caViewService.findCertificateAuthority(PRODUCTION_CA_ID)).willReturn(PRODUCTION_CA_DATA);
    }

}
