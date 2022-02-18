package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthorityTest;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AllCaCertificateUpdateServiceBeanTest extends CertificationDomainTestCase {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    private static final VersionedId PRODUCTION_CA_ID = new VersionedId(1L);

    private AllCaCertificateUpdateServiceBean subject;

    @MockBean
    private ActiveNodeService activeNodeService;

    @MockBean
    private CertificateAuthorityViewService caViewService;

    @MockBean
    private CommandService commandService;

    @MockBean
    private ResourceCache resourceCache;

    private ProductionCertificateAuthority productionCa;

    @Autowired
    private RepositoryConfiguration repositoryConfiguration;

    private CertificateAuthorityData productionCaMock;

    @Before
    public void setUp() {
        productionCa = ProductionCertificateAuthorityTest.createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        inTx(() -> createCaIfDoesntExist(productionCa));

        subject = new AllCaCertificateUpdateServiceBean(activeNodeService, caViewService, commandService, resourceCache, repositoryConfiguration);

        CertificateAuthorityData allResourcesCaMock = mock(CertificateAuthorityData.class);
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal())).thenReturn(allResourcesCaMock);

        productionCaMock = mock(CertificateAuthorityData.class);
        when(productionCaMock.getVersionedId()).thenReturn(productionCa.getVersionedId());
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal())).thenReturn(productionCaMock);
    }


    @Test
    public void shouldNotCallBackendServiceIfInactive() {
        when(activeNodeService.isActiveNode(anyString())).thenReturn(false);

        subject.execute();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldDoNothingIfProductionCaIsMissing() {
        when(activeNodeService.isActiveNode(anyString())).thenReturn(true);
        when(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_NAME)).thenReturn(null);

        subject.execute();

        verifyNoInteractions(commandService);
    }

    @Test
    public void should_dispatch_update_command_to_every_member_ca() {
        when(activeNodeService.isActiveNode(anyString())).thenReturn(true);
        CaIdentity memberCa1 = new CaIdentity(new VersionedId(10L), CaName.of(new X500Principal("CN=nl.isp")));
        CaIdentity memberCa2 = new CaIdentity(new VersionedId(11L), CaName.of(new X500Principal("CN=gr.isp")));

        when(productionCaMock.getName()).thenReturn(PRODUCTION_CA_NAME);
        when(caViewService.findAllChildrenIdsForCa(PRODUCTION_CA_NAME)).thenReturn(Arrays.asList(memberCa1, memberCa2));

        subject.execute();

        verify(commandService, times(3)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(productionCaMock.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa1.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa2.getVersionedId(), Integer.MAX_VALUE));
    }

    @Test
    public void should_not_throw_exception_if_fails() {
        doThrow(new RuntimeException("test")).when(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(PRODUCTION_CA_ID, Integer.MAX_VALUE));
        subject.execute();
    }

    @Test
    public void shouldSkipCaIfResourceCacheIsEmpty() {
        subject.runService();

        verify(resourceCache, times(1)).verifyResourcesArePresent();
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(productionCaMock.getVersionedId(), Integer.MAX_VALUE));
    }
}
