package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyManagementActivatePendingKeysCommandTest {

    private KeyManagementActivatePendingKeysCommand subject;

    @Before
    public void setUp() {
        subject = KeyManagementActivatePendingKeysCommand.plannedActivationCommand(new VersionedId(1), Duration.standardHours(24));
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Activated pending keys.", subject.getCommandSummary());
    }
}
