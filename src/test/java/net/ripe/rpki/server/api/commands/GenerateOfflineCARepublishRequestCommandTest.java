package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GenerateOfflineCARepublishRequestCommandTest {

    private GenerateOfflineCARepublishRequestCommand subject;

    @Before
    public void setUp() {
        subject = new GenerateOfflineCARepublishRequestCommand(new VersionedId(1));
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Generated Offline CA Republish Request.", subject.getCommandSummary());
    }
}
