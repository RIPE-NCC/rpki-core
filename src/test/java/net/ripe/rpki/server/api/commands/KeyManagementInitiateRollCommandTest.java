package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyManagementInitiateRollCommandTest {

    private KeyManagementInitiateRollCommand subject;

    @Before
    public void setUp() {
        subject = new KeyManagementInitiateRollCommand(new VersionedId(1), 120);
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Initiated key roll over.", subject.getCommandSummary());
    }
}
