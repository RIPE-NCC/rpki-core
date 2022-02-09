package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class CommandPersistenceHandlerTest {

    private static final VersionedId VERSIONED_CA_ID = new VersionedId(12);

    private CommandAuditService commandAuditService;
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private CommandPersistenceHandler subject;

    @Before
    public void setUp() {
        commandAuditService = mock(CommandAuditService.class);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        subject = new CommandPersistenceHandler(certificateAuthorityRepository, commandAuditService);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        Class<? extends CertificateAuthorityCommand> commandType = subject.commandType();
        assertSame(CertificateAuthorityCommand.class, commandType);
    }

    @Test
    public void shouldRecordEveryCommand() {
        UpdateAllIncomingResourceCertificatesCommand command = new UpdateAllIncomingResourceCertificatesCommand(VERSIONED_CA_ID);

        CertificateAuthority mockedCA = mock(CertificateAuthority.class);
        when(certificateAuthorityRepository.get(VERSIONED_CA_ID.getId())).thenReturn(mockedCA);
        /*
         * Yes we expect that the current versioned id of the CA is used when persisting,
         * not necessarily the version that the command had.
         * See CertificateAuthorityConcurrentModificationHandler for conflict resolution
         */
        when(mockedCA.getVersionedId()).thenReturn(VERSIONED_CA_ID);

        subject.handle(command);

        verify(commandAuditService).record(command, VERSIONED_CA_ID);
    }
}
