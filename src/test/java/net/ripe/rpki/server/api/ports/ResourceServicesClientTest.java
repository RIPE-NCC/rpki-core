package net.ripe.rpki.server.api.ports;

import java.util.Collections;

import com.google.common.collect.ImmutableMap;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.support.objects.CaName;

import org.junit.Test;

import static java.util.Arrays.*;
import static net.ripe.rpki.server.api.ports.ResourceServicesClient.*;
import static org.junit.Assert.*;


public class ResourceServicesClientTest {

    @Test
    public void shhouldGroupResourcesNyCaNames() {
        MemberResources subject = new MemberResources(
                Collections.singletonList(new AsnResource(1, "AS123", "ASSIGNED", "1")),
                asList(new Ipv4Allocation(1, "10.0.0.0/8", "ALLOCATED", "1"),
                       new Ipv4Allocation(2, "12.0.0.0/8", "ALLOCATED", "2")),
                asList(new Ipv4Assignment(1, "11.0.0.0/8", "ASSIGNED", "ORG-BLUELIGHT"),
                       new Ipv4Assignment(2, "13.0.0.0/8", "ASSIGNED", "ORG-GREENLIGHT")),
                asList(new Ipv6Allocation(1, "::1/128", "ALLOCATED", "1"),
                       new Ipv6Allocation(2, "::3/128", "ALLOCATED", "2")),
                asList(new Ipv6Assignment(1, "::2/128", "ASSIGNED", "ORG-BLUELIGHT"),
                       new Ipv6Assignment(2, "::4/128", "ASSIGNED", "ORG-GREENLIGHT")),
                Collections.singletonList(new Ipv4ErxResource("51.0.0.0/8", "ISSUED", 1L, "1")));

        ImmutableMap<CaName, IpResourceSet> expected = ImmutableMap.of(
                CaName.of(1L), IpResourceSet.parse("AS123, 10.0.0.0/8, 51.0.0.0/8, ::1/128"),
                CaName.of(2L), IpResourceSet.parse("::3/128, 12.0.0.0/8"),
                CaName.of("ORG-BLUELIGHT"), IpResourceSet.parse("11.0.0.0/8, ::2/128"),
                CaName.of("ORG-GREENLIGHT"), IpResourceSet.parse("13.0.0.0/8, ::4/128")
        );

        assertEquals(expected, subject.getCertifiableResources());
    }
}
