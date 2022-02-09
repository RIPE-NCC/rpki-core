package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnsubscribeFromRoaAlertCommandTest {

    private UnsubscribeFromRoaAlertCommand subject;

    @Before
    public void setUp() {
        subject = new UnsubscribeFromRoaAlertCommand(new VersionedId(1), "bob@example.net");
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Unsubscribed bob@example.net from ROA alerts.", subject.getCommandSummary());
    }
}
