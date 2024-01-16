package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rest.pojo.ROA;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.rest.service.Utils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class UtilsTest {
    @Test
    public void shouldCheckMaxLength() {
        assertThat(errorsInUserInputRoas(new ROA("AS65536", "192.0.2.0/24", 23)))
            .hasValue("Max length '23' must be between 24 and 32 for prefix '192.0.2.0/24'");
    }

    @Test
    public void shouldAcceptFittingMaxLength() {
        assertTrue(maxLengthIsValid(IpRange.parse("192.0.2.0/24"), 24));
        assertTrue(maxLengthIsValid(IpRange.parse("192.0.2.0/24"), 32));
        assertTrue(maxLengthIsValid(IpRange.parse("2001:DB8::/48"), 48));
        assertTrue(maxLengthIsValid(IpRange.parse("2001:DB8::/48"), 128));
    }

    @Test
    public void shouldRejectMissingMaxLength() {
        assertThat(errorsInUserInputRoas(new ROA("AS65536", "192.0.2.0/24", null)))
            .hasValue("Max length must be specified and must be between 24 and 32 for prefix '192.0.2.0/24'");
    }

    @Test
    public void shouldRejectUndershootMaxLengthV4() {
        assertFalse(maxLengthIsValid(IpRange.parse("192.0.2.0/24"), 23));
    }

    @Test
    public void shouldRejectTooLongMaxLengthV4() {
        assertFalse(maxLengthIsValid(IpRange.parse("192.0.2.0/24"), 33));
    }

    @Test
    public void shouldRejectUndershootMaxLengthV6() {
        assertFalse(maxLengthIsValid(IpRange.parse("2001:DB8::/48"), 47));
    }

    @Test
    public void shouldRejectTooLongMaxLengthV6() {
        assertFalse(maxLengthIsValid(IpRange.parse("2001:DB8::/48"), 133));
    }

    @Test
    public void shouldValidateSameROAUpdate() {
        assertEquals(Optional.empty(),
                validateNoIdenticalROAs(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        assertEquals(Optional.empty(),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.2.0/24"), null)),
                        Collections.emptyList(),
                        Collections.emptyList()));

        assertEquals(Optional.of(
                        "There is an overlap in ROAs: existing ROA{asn=AS10, prefix=192.0.1.0/24, maximumLength=28} " +
                                "has the same (ASN, prefix) as added ROA{asn='AS10', prefix='192.0.1.0/24', maximalLength=27}"),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.1.0/24"), 28)),
                        List.of(new ROA("AS10", "192.0.1.0/24", 27)),
                        Collections.emptyList()));

        assertEquals(Optional.of(
                        "There is an overlap in ROAs: existing ROA{asn=AS10, prefix=192.0.1.0/24, maximumLength=28} " +
                                "has the same (ASN, prefix) as added ROA{asn='AS10', prefix='192.0.1.0/24', maximalLength=null}"),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.1.0/24"), 28)),
                        List.of(new ROA("AS10", "192.0.1.0/24", null)),
                        Collections.emptyList()));

        assertEquals(Optional.empty(),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.1.0/24"), 28)),
                        List.of(new ROA("AS10", "192.0.1.0/24", 27)),
                        List.of(new ROA("AS10", "192.0.1.0/24", 28))
                ));

        assertEquals(Optional.empty(),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.2.0/24"), null)),
                        List.of(new ROA("AS10", "192.0.2.0/24", 27)),
                        List.of(new ROA("AS10", "192.0.2.0/24", null))
                ));

        assertEquals(Optional.empty(),
                validateNoIdenticalROAs(
                        List.of(new ExistingROA(new Asn(10L), IpRange.parse("192.0.2.0/24"), 27)),
                        List.of(new ROA("AS10", "192.0.2.0/24", null)),
                        List.of(new ROA("AS10", "192.0.2.0/24", 27))));
    }
}
