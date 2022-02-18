package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdateAllIncomingResourceCertificatesCommandTest {

    private UpdateAllIncomingResourceCertificatesCommand subject;

    @Before
    public void setUp() {
        subject = new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(1), Integer.MAX_VALUE);
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Updated all incoming certificates.", subject.getCommandSummary());
    }
}
