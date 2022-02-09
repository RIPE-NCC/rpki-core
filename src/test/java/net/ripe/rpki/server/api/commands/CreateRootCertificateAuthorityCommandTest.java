package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CreateRootCertificateAuthorityCommandTest {

    private CreateRootCertificateAuthorityCommand subject;

    @Before
    public void setUp() {
        subject = new CreateRootCertificateAuthorityCommand(new VersionedId(1));
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Created Production Certificate Authority.", subject.getCommandSummary());
    }
}
