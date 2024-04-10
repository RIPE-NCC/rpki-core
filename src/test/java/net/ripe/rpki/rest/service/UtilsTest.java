package net.ripe.rpki.rest.service;

import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rest.pojo.ApiRoaPrefix;
import org.junit.Test;

import static net.ripe.rpki.rest.service.Utils.errorsInUserInputRoas;
import static net.ripe.rpki.rest.service.Utils.maxLengthIsValid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilsTest {
    @Test
    public void shouldCheckMaxLength() {
        assertThat(errorsInUserInputRoas(new ApiRoaPrefix("AS65536", "192.0.2.0/24", 23)))
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
        assertThat(errorsInUserInputRoas(new ApiRoaPrefix("AS65536", "192.0.2.0/24", null)))
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

}
