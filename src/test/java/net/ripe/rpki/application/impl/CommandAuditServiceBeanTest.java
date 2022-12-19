package net.ripe.rpki.application.impl;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAudit;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.security.RunAsUser;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class CommandAuditServiceBeanTest extends CertificationDomainTestCase {

    @Inject
    private CommandAuditServiceBean subject;

    private static final UUID TEST_USER_UUID = UUID.randomUUID();
    private ManagedCertificateAuthority ca;
    @Before
    public void setUp() {
        clearDatabase();

        ca = createInitialisedProdCaWithRipeResources();
        RunAsUserHolder.set(RunAsUser.operator(TEST_USER_UUID));
    }

    @After
    public void tearDown() {
        RunAsUserHolder.clear();
    }

    @Test
    public void should_store_user_commands() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(ca.getVersionedId(), "", Collections.emptyList());
        CommandContext commandContext = subject.startRecording(command);
        subject.finishRecording(commandContext);

        CommandAudit commandAudit = commandContext.getCommandAudit();
        assertThat(entityManager.contains(commandAudit)).isTrue();
        assertThat(commandAudit.getPrincipal()).isEqualTo(TEST_USER_UUID.toString());
        assertThat(commandAudit.getCommandGroup()).isEqualTo(USER);
        assertThat(commandAudit.getCommandType()).isEqualTo(command.getCommandType());
        assertThat(commandAudit.getCommandSummary()).isEqualTo(command.getCommandSummary());
    }

    @Test
    public void should_store_system_commands_with_events() {
        UpdateAllIncomingResourceCertificatesCommand command = new UpdateAllIncomingResourceCertificatesCommand(ca.getVersionedId(), 1);
        CommandContext commandContext = subject.startRecording(command);
        commandContext.recordEvent("some event");
        subject.finishRecording(commandContext);

        CommandAudit commandAudit = commandContext.getCommandAudit();
        assertThat(entityManager.contains(commandAudit)).isTrue();
        assertThat(commandAudit.getCommandSummary()).isEqualTo("Updated all incoming certificates.");
        assertThat(commandAudit.getCommandEvents()).isEqualTo("some event");
    }

    @Test
    public void should_not_store_system_commands_without_events() {
        UpdateAllIncomingResourceCertificatesCommand command = new UpdateAllIncomingResourceCertificatesCommand(ca.getVersionedId(), 1);
        CommandContext commandContext = subject.startRecording(command);
        subject.finishRecording(commandContext);

        assertThat(entityManager.contains(commandContext.getCommandAudit())).isFalse();
    }

    @Test
    public void should_set_deleted_at_for_all_commands_including_current_command() {
        DeleteCertificateAuthorityCommand command = new DeleteCertificateAuthorityCommand(ca.getVersionedId(), ca.getName());
        CommandContext commandContext = subject.startRecording(command);
        commandContext.recordEvent("some event");
        subject.deleteCommandsForCa(ca.getId());
        subject.finishRecording(commandContext);

        entityManager.flush();
        entityManager.refresh(commandContext.getCommandAudit());
        assertThat(commandContext.getCommandAudit().getDeletedAt()).isNotNull();
    }
}
