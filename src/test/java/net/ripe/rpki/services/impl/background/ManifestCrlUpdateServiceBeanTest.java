package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundServiceException;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ManifestCrlUpdateServiceBeanTest {

    private CommandService commandService;
    private CertificateAuthorityViewService certificateAuthorityViewService;

    private ManifestCrlUpdateServiceBean subject;

    @Before
    public void setUp() {
        commandService = mock(CommandService.class);
        certificateAuthorityViewService = mock(CertificateAuthorityViewService.class);

        subject = new ManifestCrlUpdateServiceBean(mock(ActiveNodeService.class), commandService, certificateAuthorityViewService);
    }

    @Test
    public void should_publish_for_each_CA() throws Exception {
        CertificateAuthorityData prodCa = mock(CertificateAuthorityData.class);
        VersionedId prodCaId = new VersionedId(42L);
        when(prodCa.getVersionedId()).thenReturn(prodCaId);

        CertificateAuthorityData memberCa = mock(CertificateAuthorityData.class);
        VersionedId memberCaId = new VersionedId(43L);
        when(memberCa.getVersionedId()).thenReturn(memberCaId);

        when(certificateAuthorityViewService.findAllHostedCertificateAuthorities()).thenReturn(Arrays.asList(prodCa, memberCa));

        subject.runService();

        verify(commandService, times(2)).execute(isA(IssueUpdatedManifestAndCrlCommand.class));
        verify(commandService).execute(new IssueUpdatedManifestAndCrlCommand(prodCaId));
        verify(commandService).execute(new IssueUpdatedManifestAndCrlCommand(memberCaId));
    }

    @Test
    public void should_throw_exception_when_too_many_exceptions_are_encountered() throws Exception {

        doThrow(new RuntimeException()).when(commandService).execute(isA(IssueUpdatedManifestAndCrlCommand.class));
        List<CertificateAuthorityData> caData = createCertificateAuthorityDataMocks(15);
        when(certificateAuthorityViewService.findAllHostedCertificateAuthorities()).thenReturn(caData);

        BackgroundServiceException backgroundServiceException = assertThrows(BackgroundServiceException.class, () -> subject.runService());
        assertEquals("Too many exceptions encountered running job: 'Public Repository Management Service'. Suspecting problems that affect ALL CAs.", backgroundServiceException.getMessage());
    }

    private List<CertificateAuthorityData> createCertificateAuthorityDataMocks(int count) {
        List<CertificateAuthorityData> hostedCAs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            hostedCAs.add(mock(CertificateAuthorityData.class));
        }
        return hostedCAs;
    }
}
