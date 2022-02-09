package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UpdateRoaConfigurationCommandTest {

    private UpdateRoaConfigurationCommand subject;

    @Before
    public void setUp() {
        List<RoaConfigurationPrefixData> added = Arrays.asList(new RoaConfigurationPrefixData(Asn.parse("123"), IpRange.parse("10.64.0.0/12"), 24), new RoaConfigurationPrefixData(Asn.parse("123"), IpRange.parse("10.32.0.0/12"), null));
        subject = new UpdateRoaConfigurationCommand(new VersionedId(1), added, Collections.emptyList());
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Updated ROA configuration. Additions: [asn=AS123, prefix=10.32.0.0/12, maximumLength=12], [asn=AS123, prefix=10.64.0.0/12, maximumLength=24]. Deletions: none.", subject.getCommandSummary());
    }
}
