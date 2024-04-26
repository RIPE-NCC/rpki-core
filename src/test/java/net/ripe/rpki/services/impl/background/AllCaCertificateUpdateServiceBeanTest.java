package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AllCaCertificateUpdateServiceBeanTest {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    private static final VersionedId PRODUCTION_CA_ID = new VersionedId(1L);
    private static final CertificateAuthorityData PRODUCTION_CA = new ManagedCertificateAuthorityData(
        PRODUCTION_CA_ID, PRODUCTION_CA_NAME, UUID.randomUUID(),
        null, CertificateAuthorityType.ROOT,
        ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());
    public static final CertificateAuthorityData MEMBER_CA_1 = new HostedCertificateAuthorityData(new VersionedId(10L),
        new X500Principal("CN=nl.isp"), UUID.randomUUID(), 2L,
        ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());
    public static final CertificateAuthorityData MEMBER_CA_2 = new HostedCertificateAuthorityData(new VersionedId(11L),
        new X500Principal("CN=gr.isp"), UUID.randomUUID(), 2L,
        ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());
    private static final X500Principal ALL_RESOURCES_CA_NAME = new X500Principal("CN=All Resources CA,O=RIPE NCC,C=NL");

    private static final Random RANDOM = new Random();

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

    @Before
    public void setUp() {
        subject = new AllCaCertificateUpdateServiceBean(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()), caViewService, commandService, resourceCache, repositoryConfiguration, 1000, new SimpleMeterRegistry());

        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(ALL_RESOURCES_CA_NAME);
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(PRODUCTION_CA_NAME);

        when(activeNodeService.isActiveNode()).thenReturn(true);
        when(commandService.execute(isA(CertificateAuthorityCommand.class))).thenReturn(CommandStatus.create());

        CertificateAuthorityData allResourcesCaMock = mock(CertificateAuthorityData.class);
        when(caViewService.findCertificateAuthorityByName(ALL_RESOURCES_CA_NAME)).thenReturn(allResourcesCaMock);

        when(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA);
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
        when(caViewService.findAllChildrenForCa(PRODUCTION_CA_NAME)).thenReturn(Arrays.asList(MEMBER_CA_1, MEMBER_CA_2));

        subject.execute(Collections.emptyMap());

        verify(commandService, times(4)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService, times(2)).execute(new UpdateAllIncomingResourceCertificatesCommand(PRODUCTION_CA.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(MEMBER_CA_1.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(MEMBER_CA_2.getVersionedId(), Integer.MAX_VALUE));
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
        when(caViewService.findAllChildrenForCa(PRODUCTION_CA_NAME)).thenReturn(Collections.singletonList(MEMBER_CA_1));
        when(commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(MEMBER_CA_1.getVersionedId(), Integer.MAX_VALUE)))
            .thenThrow(new EntityNotFoundException());

        subject.execute(Collections.emptyMap());

        verify(commandService, times(2)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService, times(1)).execute(new UpdateAllIncomingResourceCertificatesCommand(PRODUCTION_CA.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(MEMBER_CA_1.getVersionedId(), Integer.MAX_VALUE));
    }

    @Test
    public void should_process_all_cas_in_arbitrary_tree() {
        var cas = generateTree(PRODUCTION_CA, 3);
        when(commandService.execute(any(UpdateAllIncomingResourceCertificatesCommand.class))).thenReturn(CommandStatus.create());

        subject.execute(Collections.emptyMap());

        for (var caWithChildren : cas.entrySet()) {
            boolean isLeaf = caWithChildren.getValue().isEmpty();
            verify(commandService, times(isLeaf ? 1 : 2)).execute(new UpdateAllIncomingResourceCertificatesCommand(caWithChildren.getKey().getVersionedId(), Integer.MAX_VALUE));
        }
    }

    private Map<CertificateAuthorityData, Collection<CertificateAuthorityData>> generateTree(CertificateAuthorityData parent, int maxDepth) {
        if (maxDepth == 0) {
            return Collections.emptyMap();
        }

        int childCount = maxDepth / 2 + RANDOM.nextInt(10);
        var children = new ArrayList<CertificateAuthorityData>(childCount);
        for (int i = 0; i < childCount; ++i) {
            long id = RANDOM.nextLong();
            var child = new HostedCertificateAuthorityData(
                new VersionedId(id),
                new X500Principal("CN=ORG-" + id),
                UUID.randomUUID(),
                parent.getId(),
                ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES,
                Collections.emptyList()
            );
            children.add(child);
        }

        when(caViewService.findAllChildrenForCa(parent.getName())).thenReturn(children);

        var result = new HashMap<CertificateAuthorityData, Collection<CertificateAuthorityData>>();
        result.put(parent, children);
        for (CertificateAuthorityData child : children) {
            result.putAll(generateTree(child, maxDepth - 1));
        }

        return result;
    }
}
