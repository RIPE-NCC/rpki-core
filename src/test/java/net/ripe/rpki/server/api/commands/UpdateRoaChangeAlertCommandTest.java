package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateRoaChangeAlertCommandTest {

    @Test
    void getCommandSummary_shouldIndicateUnsubscriptionWhenNotifyOnRoaChangesIsFalse() {
        var emails = Set.of("test@example.com");
        var command = new UpdateRoaChangeAlertCommand(new VersionedId(1L, 1), emails, false);
        assertEquals("Unsubscribed test@example.com from ROA change alerts.", command.getCommandSummary());
    }

    @Test
    void getCommandSummary_shouldHandleMultipleEmails() {
        var emails = Set.of("test@example.com", "user@example.com");
        var command = new UpdateRoaChangeAlertCommand(new VersionedId(1L, 1), emails, true);
        assertEquals("Subscribed test@example.com, user@example.com for ROA change alerts.", command.getCommandSummary());
    }
}