package net.ripe.rpki.ui.application;


import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.*;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ROOT;
import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
public abstract class AbstractCertificationWicketTest {

    protected WicketTester tester;

    protected static final X500Principal PRODUCTION_CA_PRINCIPAL = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    protected static final long PRODUCTION_CA_ID = 42l;
    protected static final VersionedId PRODUCTION_CA_VERSIONED_ID = new VersionedId(PRODUCTION_CA_ID, 3);

    protected static final ImmutableResourceSet RESOURCES = ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES.union(
        ImmutableResourceSet.parse("2.0.0.0/8,3.0.0.0/8,4.0.0.0/8,5.0.0.0/8")
    );

    protected static final CertificateAuthorityData PRODUCTION_CA_DATA = new ManagedCertificateAuthorityData(
        PRODUCTION_CA_VERSIONED_ID, PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), null, ROOT, RESOURCES, Collections.emptyList());


    protected static final X500Principal ALL_RESOURCES_CA_PRINCIPAL = new X500Principal("CN=ALL Resources,O=RIPE NCC,C=NL");

    @MockBean
    protected CertificateAuthorityViewService caViewService;
    @MockBean
    protected ResourceCertificateViewService certViewService;
    @MockBean
    protected RoaViewService roaService;
    @MockBean
    protected RepositoryConfiguration repositoryConfiguration;
    @MockBean
    protected RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    @MockBean
    protected BgpRisEntryViewService bgpRisEntryRepository;
    @MockBean
    protected CommandService commandService;
    @MockBean
    protected InternalNamePresenter statsCollectorNames;
    @MockBean(name = ALL_CA_CERTIFICATE_UPDATE_SERVICE)
    protected BackgroundService allCertificateUpdateService;
    @MockBean
    protected ActiveNodeService activeNodeService;
    @MockBean
    protected ResourceLookupService resourceLookupService;
    @MockBean
    protected CertificateAuthorityCreateService certificateAuthorityCreateService;
    @Inject
    protected ApplicationContext applicationContextMock;


    @Before
    public final void setUpMockCertificationService() {
        System.setProperty("disable.development.user", "true");
        AuthenticatedWebSession.unset(); // Clean up the current threadlocal variables

        ImmutableResourceSet resources = ImmutableResourceSet.parse("2.0.0.0/8, 5.0.0.0/8, 37.0.0.0/8, 46.0.0.0/8, " +
            "62.0.0.0/8, 77.0.0.0-95.255.255.255, 109.0.0.0/8, 141.0.0.0/8, 145.0.0.0/8, 151.0.0.0/8, 178.0.0.0/8, " +
            "188.0.0.0/8, 193.0.0.0-195.255.255.255, 196.200.0.0/13, 212.0.0.0/7, 217.0.0.0/8, " +
            "2001:600::-2001:bff:ffff:ffff:ffff:ffff:ffff:ffff, " +
            "2001:1400::/22, 2001:1a00::-2001:3bff:ffff:ffff:ffff:ffff:ffff:ffff, " +
            "2001:4000::/23, 2001:4600::/23, 2001:4a00::-2001:4dff:ffff:ffff:ffff:ffff:ffff:ffff, " +
            "2001:5000::/20, 2003::/18, 2a00::/12, 3.0.0.0/24, 4.0.0.0/24, 6.0.0.0/24, 7.0.0.0/16, 8.0.0.0/24, 9.0.0.0/24");

        given(resourceLookupService.lookupProductionCaResources()).willReturn(resources);
        given(activeNodeService.getCurrentNodeName()).willReturn("current-node");

        tester = new WicketTester(getApplicationStub());
    }

    protected WebApplication getApplicationStub() {
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
