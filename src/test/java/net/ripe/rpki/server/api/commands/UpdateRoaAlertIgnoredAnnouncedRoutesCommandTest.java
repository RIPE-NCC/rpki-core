package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class UpdateRoaAlertIgnoredAnnouncedRoutesCommandTest {

    private UpdateRoaAlertIgnoredAnnouncedRoutesCommand subject;

    @Before
    public void setUp() {
        Collection<AnnouncedRoute> added = Collections.singletonList(new AnnouncedRoute(Asn.parse("123"), IpRange.parse("10/8")));
        subject = new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(new VersionedId(1), added, Collections.emptyList());
    }

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        assertEquals("Updated suppressed routes for ROA alerts. Additions: [asn=AS123, prefix=10.0.0.0/8]. Deletions: none.", subject.getCommandSummary());
    }
}
