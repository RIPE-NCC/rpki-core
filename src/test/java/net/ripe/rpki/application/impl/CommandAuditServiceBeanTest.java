package net.ripe.rpki.application.impl;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.audit.CommandAudit;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.security.CertificationUserId;
import net.ripe.rpki.server.api.security.RoleBasedAuthenticationStrategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.persistence.EntityManager;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommandAuditServiceBeanTest {

    private EntityManager entityManager;
    private CommandAuditServiceBean subject;
    private RoleBasedAuthenticationStrategy authStrategy;

    private static final String TEST_USER_UUID = "6e80bc78-7f56-407a-be41-3d3f76af2919";
    private static final CertificationUserId TEST_USER = new CertificationUserId(UUID.fromString(TEST_USER_UUID));

    @Before
    public void setUp() {
        entityManager = mock(EntityManager.class);
        authStrategy = mock(RoleBasedAuthenticationStrategy.class);
        subject = new CommandAuditServiceBean(authStrategy, entityManager);
    }

    @Test
    public void shouldRecordUserCommands() {
        when(authStrategy.getOriginalUserId()).thenReturn(TEST_USER);
        ArgumentCaptor<CommandAudit> capturedArgument = ArgumentCaptor.forClass(CommandAudit.class);

        CreateRootCertificateAuthorityCommand command = new CreateRootCertificateAuthorityCommand(new VersionedId(12));
        subject.record(command, new VersionedId(12));

        verify(entityManager).persist(capturedArgument.capture());

        CommandAudit commandAudit = capturedArgument.getValue();

        assertEquals(TEST_USER_UUID, commandAudit.getPrincipal());
        assertEquals(command.getCertificateAuthorityVersionedId(), commandAudit.getCertificateAuthorityVersionedId());
        assertEquals(command.getCertificateAuthorityId(), commandAudit.getCertificateAuthorityId());

        assertEquals(command.getCommandType(), commandAudit.getCommandType());
        assertEquals(command.getCommandGroup(), commandAudit.getCommandGroup());
        assertEquals(command.getCommandSummary(), commandAudit.getCommandSummary());
    }

    @Test
    public void shouldNotRecordSystemCommands() {
        when(authStrategy.getOriginalUserId()).thenReturn(TEST_USER);

        KeyManagementInitiateRollCommand command = new KeyManagementInitiateRollCommand(new VersionedId(1), 120);
        subject.record(command, new VersionedId(12));

        verifyNoInteractions(entityManager);
    }
}
