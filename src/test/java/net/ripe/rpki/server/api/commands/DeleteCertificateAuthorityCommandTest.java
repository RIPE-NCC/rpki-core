package net.ripe.rpki.server.api.commands;

import com.google.common.collect.Lists;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;

public class DeleteCertificateAuthorityCommandTest {

    private DeleteCertificateAuthorityCommand subject;
    private RoaConfigurationData roaConfiguration = new RoaConfigurationData(Collections.singletonList(new RoaConfigurationPrefixData(
            new Asn(3333L), new RoaPrefix(IpRange.prefix(Ipv4Address.parse("10.0.0.0"), 8))
    )));

    @Before
    public void setUp() {
        subject = new DeleteCertificateAuthorityCommand(new VersionedId(1), new X500Principal("CN=Foo"), roaConfiguration);
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        then(subject.getCommandSummary()).contains("Deleted Certificate Authority");
        then(subject.getCommandSummary()).contains("CN=Foo");
        then(subject.getCommandSummary()).contains("10.0.0.0/8");
    }
}
