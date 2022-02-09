package net.ripe.rpki.domain.property;

import org.junit.Test;


public class PropertyEntityTest {

    @Test(expected=IllegalArgumentException.class)
    public void shouldRequireKey() {
        new PropertyEntity(null, "value");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRequireValue() {
        new PropertyEntity("key", null);
    }
}
