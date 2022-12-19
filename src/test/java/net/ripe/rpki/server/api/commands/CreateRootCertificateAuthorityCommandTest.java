package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.TestObjects;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CreateRootCertificateAuthorityCommandTest {

    private CreateRootCertificateAuthorityCommand subject;

    @Before
    public void setUp() {
        subject = new CreateRootCertificateAuthorityCommand(new VersionedId(1), TestObjects.PRODUCTION_CA_NAME);
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Created Production Certificate Authority 'O=ORG-TEST-PRODUCTION-CA'.", subject.getCommandSummary());
    }
}
