package net.ripe.rpki.rest.service;

import net.ripe.rpki.rest.pojo.ROA;
import org.junit.Test;

import static net.ripe.rpki.rest.service.Utils.lengthIsValid;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilsTest {
    @Test
    public void shouldAcceptFittingMaxLength() {
        assertTrue(lengthIsValid(new ROA("AS65536", "192.0.2.0/24", 24)));
        assertTrue(lengthIsValid(new ROA("AS65536", "192.0.2.0/24", 32)));
        assertTrue(lengthIsValid(new ROA("AS65536", "2001:DB8::/48", 48)));
        assertTrue(lengthIsValid(new ROA("AS65536", "2001:DB8::/48", 128)));
    }

    @Test
    public void shouldRejectMissingMaxLength() {
        assertFalse(lengthIsValid(new ROA("AS65536", "192.0.2.0/24", null)));
    }

    @Test
    public void shouldRejectUndershootMaxLengthV4() {
        assertFalse(lengthIsValid(new ROA("AS65536", "192.0.2.0/24", 23)));
    }

    @Test
    public void shouldRejectTooLongMaxLengthV4() {
        assertFalse(lengthIsValid(new ROA("AS65536", "192.0.2.0/24", 33)));
    }

    @Test
    public void shouldRejectUndershootMaxLengthV6() {
        assertFalse(lengthIsValid(new ROA("AS65536", "2001:DB8::/48", 47)));
    }

    @Test
    public void shouldRejectTooLongMaxLengthV6() {
        assertFalse(lengthIsValid(new ROA("AS65536", "2001:DB8::/48", 133)));
    }
}
