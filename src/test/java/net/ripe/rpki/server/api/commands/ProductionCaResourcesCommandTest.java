package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProductionCaResourcesCommandTest {

    private IpResourceSet resources = IpResourceSet.parse("2/8, 10/8, 192.168/16");

    private ProductionCaResourcesCommand subject;

    @Before
    public void setUp() {
        subject = new ProductionCaResourcesCommand(new VersionedId(1), resources);
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Updated Certificate Authority resources to 2.0.0.0/8, 10.0.0.0/8, 192.168.0.0/16", subject.getCommandSummary());
    }
}
