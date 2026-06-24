package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AdminDeleteCertificateAuthorityCommandTest {
    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Delete Certificate Authority 'CN=Foo' by 'Fernando Esteban'.",
                new AdminDeleteCertificateAuthorityCommand(
                        new VersionedId(1), new X500Principal("CN=Foo"), "Fernando Esteban")
                        .getCommandSummary());
    }
}
