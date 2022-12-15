package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AllCaCertificateUpdateServiceBeanTest {

    private static final X500Principal ALL_RESOURCES_CA_NAME = new X500Principal("CN=All Resources CA,O=RIPE NCC,C=NL");
    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    private static final VersionedId PRODUCTION_CA_ID = new VersionedId(1L);

    private AllCaCertificateUpdateServiceBean subject;

    @Mock
    private ActiveNodeService activeNodeService;

    @Mock
    private CertificateAuthorityViewService caViewService;

    @Mock
    private CommandService commandService;

    @Mock
    private ResourceCache resourceCache;

    @Mock
    private RepositoryConfiguration repositoryConfiguration;

    private CertificateAuthorityData productionCaMock;

    @Before
    public void setUp() {
        subject = new AllCaCertificateUpdateServiceBean(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()), caViewService, commandService, resourceCache, repositoryConfiguration, 1000);

        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(ALL_RESOURCES_CA_NAME);
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(PRODUCTION_CA_NAME);

        when(activeNodeService.isActiveNode()).thenReturn(true);
        when(commandService.execute(isA(CertificateAuthorityCommand.class))).thenReturn(CommandStatus.create());

        CertificateAuthorityData allResourcesCaMock = mock(CertificateAuthorityData.class);
        when(caViewService.findCertificateAuthorityByName(ALL_RESOURCES_CA_NAME)).thenReturn(allResourcesCaMock);

        productionCaMock = mock(CertificateAuthorityData.class);
        when(productionCaMock.getName()).thenReturn(PRODUCTION_CA_NAME);
        when(productionCaMock.getVersionedId()).thenReturn(PRODUCTION_CA_ID);

        when(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_NAME)).thenReturn(productionCaMock);
    }


    @Test
    public void shouldNotCallBackendServiceIfInactive() {
        when(activeNodeService.isActiveNode()).thenReturn(false);

        subject.execute(Collections.emptyMap());

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldDoNothingIfProductionCaIsMissing() {
        when(activeNodeService.isActiveNode()).thenReturn(true);
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal())).thenReturn(null);

        subject.execute(Collections.emptyMap());

        verifyNoInteractions(commandService);
    }

    @Test
    public void should_dispatch_update_command_to_every_member_ca_that_needs_an_update() {
        CaIdentity memberCa1 = new CaIdentity(new VersionedId(10L), CaName.of(new X500Principal("CN=nl.isp")));
        CaIdentity memberCa2 = new CaIdentity(new VersionedId(11L), CaName.of(new X500Principal("CN=gr.isp")));
        when(caViewService.findAllChildrenIdsForCa(PRODUCTION_CA_NAME)).thenReturn(Arrays.asList(memberCa1, memberCa2));

        subject.execute(Collections.emptyMap());

        verify(commandService, times(4)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService, times(2)).execute(new UpdateAllIncomingResourceCertificatesCommand(productionCaMock.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa1.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa2.getVersionedId(), Integer.MAX_VALUE));
    }

    @Test
    public void should_not_throw_exception_if_command_fails() {
        doThrow(new RuntimeException("test")).when(commandService).execute(any());

        subject.execute(Collections.emptyMap());

        verify(commandService).execute(any());
    }

    @Test
    public void shouldSkipCaIfResourceCacheIsEmpty() {
        doThrow(new IllegalStateException("TEST")).when(resourceCache).verifyResourcesArePresent();

        assertThatThrownBy(() -> subject.runService(Collections.emptyMap())).isInstanceOf(IllegalStateException.class);

        verify(resourceCache, times(1)).verifyResourcesArePresent();
        verifyNoInteractions(commandService);
    }

    @Test
    public void should_ignore_error_when_ca_is_deleted_during_run() {
        CaIdentity memberCa1 = new CaIdentity(new VersionedId(10L), CaName.of(new X500Principal("CN=nl.isp")));
        when(caViewService.findAllChildrenIdsForCa(PRODUCTION_CA_NAME)).thenReturn(Collections.singletonList(memberCa1));
        when(commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa1.getVersionedId(), Integer.MAX_VALUE)))
            .thenThrow(new EntityNotFoundException());

        subject.execute(Collections.emptyMap());

        verify(commandService, times(2)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService, times(1)).execute(new UpdateAllIncomingResourceCertificatesCommand(productionCaMock.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa1.getVersionedId(), Integer.MAX_VALUE));
    }
}
