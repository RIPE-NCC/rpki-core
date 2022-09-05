package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertEquals;

public class ActivateHostedCertificateAuthorityCommandTest {

    private ActivateHostedCertificateAuthorityCommand subject;

    @Test
    public void shouldHaveDescriptiveLogEntryWithResources() {
        subject = new ActivateHostedCertificateAuthorityCommand(new VersionedId(1), new X500Principal("cn=zz.example"), IpResourceSet.parse("10/8, 192.168/16"), 0);
        assertEquals("Created and activated Certificate Authority 'CN=zz.example' with resources 10.0.0.0/8, 192.168.0.0/16", subject.getCommandSummary());
    }
}
