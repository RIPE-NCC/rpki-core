package net.ripe.rpki.ui.application;


import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
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

import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ALL_RESOURCES;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ROOT;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
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
    @MockBean
    protected CertificateAuthorityViewService caViewService;
    @MockBean
    protected RepositoryConfiguration repositoryConfiguration;
    @MockBean
    protected CommandService commandService;
    @MockBean
    protected InternalNamePresenter statsCollectorNames;
    @MockBean
    protected ActiveNodeService activeNodeService;
    @Inject
    protected ApplicationContext applicationContextMock;


    @Before
    public final void setUpMockCertificationService() {
        System.setProperty("disable.development.user", "true");

        // Clean up the current threadlocal variables
        AuthenticatedWebSession.unset();

        when(activeNodeService.getCurrentNodeName()).thenReturn("current-node");
        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(ALL_RESOURCES_CA_NAME);
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(PRODUCTION_CA_NAME);
        when(caViewService.findCertificateAuthorityByName(ALL_RESOURCES_CA_NAME)).thenReturn(ALL_RESOURCES_CA_DATA);
        when(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_DATA);

        tester = new WicketTester(getApplicationStub());
    }

    protected WebApplication getApplicationStub() {
        return new CertificationAdminWicketApplicationStub(applicationContextMock);
    }

    @After
    public final void cleanUp() {
        System.clearProperty("disable.development.user");
    }
}
