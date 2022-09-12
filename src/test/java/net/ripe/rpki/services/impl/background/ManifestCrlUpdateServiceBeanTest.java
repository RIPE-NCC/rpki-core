package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.core.services.background.BackgroundTaskRunner.MAX_ALLOWED_EXCEPTIONS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ManifestCrlUpdateServiceBeanTest {

    private CommandService commandService;
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private ManifestCrlUpdateServiceBean subject;

    @Before
    public void setUp() {
        commandService = mock(CommandService.class);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);

        ActiveNodeService activeNodeService = mock(ActiveNodeService.class);
        when(activeNodeService.isActiveNode()).thenReturn(true);

        subject = new ManifestCrlUpdateServiceBean(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()), commandService, certificateAuthorityRepository, 10);
    }

    @Test
    public void should_publish_for_each_CA() {
        ManagedCertificateAuthority prodCa = mock(ManagedCertificateAuthority.class);
        VersionedId prodCaId = new VersionedId(42L);
        when(prodCa.getVersionedId()).thenReturn(prodCaId);

        ManagedCertificateAuthority memberCa = mock(ManagedCertificateAuthority.class);
        VersionedId memberCaId = new VersionedId(43L);
        when(memberCa.getVersionedId()).thenReturn(memberCaId);

        when(certificateAuthorityRepository.findAllWithOutdatedManifests(any(), anyInt()))
            .thenReturn(Arrays.asList(prodCa, memberCa))
            .thenReturn(Collections.emptyList());

        subject.execute();

        verify(commandService, times(2)).execute(isA(IssueUpdatedManifestAndCrlCommand.class));
        verify(commandService).execute(new IssueUpdatedManifestAndCrlCommand(prodCaId));
        verify(commandService).execute(new IssueUpdatedManifestAndCrlCommand(memberCaId));
    }

    @Test
    public void should_throw_exception_when_too_many_exceptions_are_encountered() {
        doThrow(new RuntimeException()).when(commandService).execute(isA(IssueUpdatedManifestAndCrlCommand.class));
        List<ManagedCertificateAuthority> caData = createCertificateAuthorityDataMocks(MAX_ALLOWED_EXCEPTIONS + 1);
        when(certificateAuthorityRepository.findAllWithOutdatedManifests(any(), anyInt())).thenReturn(caData);

        BackgroundServiceExecutionResult result = subject.execute();

        assertThat(result.getStatus()).isEqualTo(BackgroundServiceExecutionResult.Status.FAILURE);
    }

    private List<ManagedCertificateAuthority> createCertificateAuthorityDataMocks(int count) {
        List<ManagedCertificateAuthority> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(mock(ManagedCertificateAuthority.class));
        }
        return result;
    }
}
