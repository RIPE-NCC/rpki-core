package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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

}