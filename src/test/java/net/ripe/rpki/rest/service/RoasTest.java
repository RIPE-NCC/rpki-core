package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;
import net.ripe.rpki.rest.pojo.PublishSet;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoasTest {

    @Test
    public void testValidateApplyDiff() {
        assertEquals(
                Set.of(new AllowedRoute(new Asn(11L), IpRange.parse("192.0.2.0/24"), 24)),
                Roas.applyDiff(
                        Set.of(new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24)),
                        new Roas.RoaDiff(
                                Set.of(new AllowedRoute(new Asn(11L), IpRange.parse("192.0.2.0/24"), 24)),
                                Set.of(new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24))
                        )
                ));
    }

    @Test
    public void testValidateRoaUpdate() {
        assertEquals(Optional.empty(), Roas.validateRoaUpdate(Collections.emptySet()));

        // No changes
        assertEquals(Optional.empty(),
                Roas.validateRoaUpdate(Set.of(new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24))));

        // Add a couple of ROAs
        assertEquals(Optional.empty(),
                Roas.validateRoaUpdate(
                        Set.of(
                                new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24),
                                new AllowedRoute(new Asn(11L), IpRange.parse("193.0.2.0/24"), 24),
                                new AllowedRoute(new Asn(12L), IpRange.parse("194.0.2.0/24"), 24)
                        )));

        // Delete a couple of ROAs
        assertEquals(Optional.empty(),
                Roas.validateRoaUpdate(
                        Set.of(new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24))));

        // Try to add maxLength duplicates
        assertEquals(Optional.of("Error in future ROAs: there are more than one pair (AS10, 192.0.2.0/24), max lengths: [24, 25]"),
                Roas.validateRoaUpdate(
                        Set.of(
                                new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 24),
                                new AllowedRoute(new Asn(10L), IpRange.parse("192.0.2.0/24"), 25)
                        )));
    }


    @Test
    public void testProperDiff() {
        var roa1 = new ApiRoaPrefix("AS1", "143.161.246.0/24", 24);
        var roa2 = new ApiRoaPrefix("AS2", "143.161.246.0/24", 24);
        var roa3 = new ApiRoaPrefix("AS3", "142.161.247.0/24", 24);
        var publishSet = new PublishSet();
        publishSet.setAdded(List.of(roa1, roa2));
        publishSet.setDeleted(List.of(roa1, roa3));
        Roas.RoaDiff diff = Roas.toDiff(publishSet);

        assertEquals(1, diff.getAdded().size());
        assertTrue(diff.getAdded().contains(Roas.toAllowedRoute(roa2)));
        assertEquals(1, diff.getDeleted().size());
        assertTrue(diff.getDeleted().contains(Roas.toAllowedRoute(roa3)));
    }

    @Test
    public void testCancelOutUpdate() {
        // Based on a real bug report
        var roa1 = new ApiRoaPrefix("AS702", "143.161.246.0/24", 24);
        var roa2 = new ApiRoaPrefix("AS1759", "143.161.246.0/24", 24);
        var publishSet = new PublishSet();
        publishSet.setAdded(List.of(roa1, roa2));
        publishSet.setDeleted(List.of(roa1, roa2));
        var currentRoas = Set.of(
                new AllowedRoute(Asn.parse("AS702"), IpRange.parse("143.161.246.0/24"), 24),
                new AllowedRoute(Asn.parse("AS1759"), IpRange.parse("143.161.246.0/24"), 24)
        );
        Roas.RoaDiff diff = Roas.toDiff(publishSet);

        assertTrue(diff.getAdded().isEmpty());
        assertTrue(diff.getDeleted().isEmpty());
        assertEquals(currentRoas, Roas.applyDiff(currentRoas, diff));
    }

}