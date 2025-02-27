package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class UnsubscribeFromRoaAlertCommandTest {

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        Function<Boolean, UnsubscribeFromRoaAlertCommand> makeCommand = notify -> new UnsubscribeFromRoaAlertCommand(new VersionedId(1), "bob@example.net", notify);
        assertEquals("Unsubscribed bob@example.net from ROA alerts.", makeCommand.apply(false).getCommandSummary());
        assertEquals("Unsubscribed bob@example.net from ROA alerts and ROA changes.", makeCommand.apply(true).getCommandSummary());
    }
}
