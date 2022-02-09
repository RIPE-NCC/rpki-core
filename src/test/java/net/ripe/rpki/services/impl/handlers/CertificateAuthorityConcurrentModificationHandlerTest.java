package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityConcurrentModificationException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class CertificateAuthorityConcurrentModificationHandlerTest {

    private static final VersionedId TEST_CA_VERSIONED_ID = new VersionedId(12, 34);

    private HostedCertificateAuthority certificateAuthority;
    private CertificateAuthorityRepository certificateAuthorityRepository;
    private CommandAuditService commandAuditService;

    private CertificateAuthorityConcurrentModificationHandler subject;

    @Before
    public void setUp() {
        certificateAuthority = mock(HostedCertificateAuthority.class);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        commandAuditService = mock(CommandAuditService.class);

        subject = new CertificateAuthorityConcurrentModificationHandler(certificateAuthorityRepository, commandAuditService, new SimpleMeterRegistry());

        when(certificateAuthority.getVersionedId()).thenReturn(TEST_CA_VERSIONED_ID);
        when(certificateAuthority.getId()).thenReturn(TEST_CA_VERSIONED_ID.getId());
        when(certificateAuthorityRepository.get(certificateAuthority.getId())).thenReturn(certificateAuthority);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        Class<? extends CertificateAuthorityCommand> commandType = subject.commandType();
        assertSame(CertificateAuthorityModificationCommand.class, commandType);
    }

    @Test
    public void shouldFailOnVersionMismatch() {
        GenerateOfflineCARepublishRequestCommand command = new GenerateOfflineCARepublishRequestCommand(TEST_CA_VERSIONED_ID);

        List<CommandAuditData> conflictingCommands = Collections.singletonList(
                new CommandAuditData(new DateTime(), TEST_CA_VERSIONED_ID, "CN=meme", "delete roa", CertificateAuthorityCommandGroup.USER, new UpdateRoaConfigurationCommand(TEST_CA_VERSIONED_ID, Collections.emptyList(), Collections.emptyList()).getCommandSummary())
        );
        when(commandAuditService.findCommandsSinceCaVersion(TEST_CA_VERSIONED_ID)).thenReturn(conflictingCommands);

        try {
            subject.handle(command);
            fail("CertificateAuthorityConcurrentModificationException expected");
        } catch (CertificateAuthorityConcurrentModificationException expected) {
            assertSame(command, expected.getCommand());
            assertEquals(certificateAuthority.getVersionedId().getVersion(), expected.getCurrentCertificateAuthorityVersion());
            assertEquals(conflictingCommands, expected.getConflictingCommands());
        }
    }

    @Test
    public void shouldAcceptSystemCommandEvenWhenVersionsMismatch() {
        long originalVersion = certificateAuthority.getVersionedId().getVersion();

        List<CommandAuditData> conflictingCommands = Collections.singletonList(
                new CommandAuditData(new DateTime(), TEST_CA_VERSIONED_ID, "CN=meme", "delete roa", CertificateAuthorityCommandGroup.USER, new UpdateRoaConfigurationCommand(TEST_CA_VERSIONED_ID, Collections.emptyList(), Collections.emptyList()).getCommandSummary())
        );
        when(commandAuditService.findCommandsSinceCaVersion(TEST_CA_VERSIONED_ID)).thenReturn(conflictingCommands);

        subject.handle(new IssueUpdatedManifestAndCrlCommand(TEST_CA_VERSIONED_ID));
    }

    @Test
    public void should_Accept_UpdateRoaConfigurationCommand_After_UpdateRoaAlertIgnoredAnnouncedRoutesCommand() {
        long originalVersion = certificateAuthority.getVersionedId().getVersion();

        // Given an existing update alerts command for some CA versioned id
        List<CommandAuditData> commandsSinceOldCaVersion = Collections.singletonList(
                new CommandAuditData(new DateTime(), TEST_CA_VERSIONED_ID, "CN=meme", "UpdateRoaAlertIgnoredAnnouncedRoutesCommand", CertificateAuthorityCommandGroup.USER, new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(TEST_CA_VERSIONED_ID, Collections.emptySet(), Collections.emptySet()).getCommandSummary())
        );
        when(commandAuditService.findCommandsSinceCaVersion(TEST_CA_VERSIONED_ID)).thenReturn(commandsSinceOldCaVersion);

        // When the user submits an update roa config command for that same CA versioned id
        subject.handle(new UpdateRoaConfigurationCommand(TEST_CA_VERSIONED_ID, Collections.emptySet(), Collections.emptySet()));
    }

}
