package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IssueUpdatedManifestAndCrlCommandHandlerTest {

    private IssueUpdatedManifestAndCrlCommandHandler subject;

    @Mock
    private ManagedCertificateAuthority rootCa;
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private RoaEntityService roaEntityService;
    @Mock
    private CertificateManagementService certificateManagementService;

    @Before
    public void setUp() {
        VersionedId versionedId = new VersionedId(1L, 1L);
        when(rootCa.getId()).thenReturn(versionedId.getId());
        when(rootCa.getVersionedId()).thenReturn(versionedId);
        when(certificateAuthorityRepository.findManagedCa(rootCa.getId())).thenReturn(rootCa);

        this.subject = new IssueUpdatedManifestAndCrlCommandHandler(certificateAuthorityRepository, roaEntityService, certificateManagementService);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertEquals(IssueUpdatedManifestAndCrlCommand.class, subject.commandType());
    }

    @Test
    public void should_delegate_to_certificateManagementService() {
        when(certificateManagementService.updateManifestAndCrlIfNeeded(rootCa)).thenReturn(1L);

        subject.handle(new IssueUpdatedManifestAndCrlCommand(rootCa.getVersionedId()));

        verify(roaEntityService).updateRoasIfNeeded(rootCa);
        verify(certificateManagementService).updateManifestAndCrlIfNeeded(rootCa);
    }

    @Test
    public void should_have_no_affect_when_update_is_not_needed() {
        when(certificateManagementService.updateManifestAndCrlIfNeeded(rootCa)).thenReturn(0L);

        assertThrows(CommandWithoutEffectException.class, () -> subject.handle(new IssueUpdatedManifestAndCrlCommand(rootCa.getVersionedId())));
    }
}
