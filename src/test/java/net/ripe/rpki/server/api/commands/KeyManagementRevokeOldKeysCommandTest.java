package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyManagementRevokeOldKeysCommandTest {


    private KeyManagementRevokeOldKeysCommand subject;

    @Before
    public void setUp() {
        subject = new KeyManagementRevokeOldKeysCommand(new VersionedId(1));
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Revoked old keys.", subject.getCommandSummary());
    }
}
