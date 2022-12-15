package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.BDDAssertions.then;

public class DeleteCertificateAuthorityCommandTest {

    private DeleteCertificateAuthorityCommand subject;

    @Before
    public void setUp() {
        subject = new DeleteCertificateAuthorityCommand(new VersionedId(1), new X500Principal("CN=Foo"));
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        then(subject.getCommandSummary()).contains("Deleted Certificate Authority");
        then(subject.getCommandSummary()).contains("CN=Foo");
    }
}
