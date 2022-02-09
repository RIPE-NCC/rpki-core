package net.ripe.rpki.server.api.dto;

import com.google.common.collect.Sets;
import net.ripe.ipresource.IpResourceSet;
import org.junit.Test;

import java.util.Collections;

import static net.ripe.rpki.server.api.dto.ResourceClassMap.*;
import static org.junit.Assert.*;

public class ResourceClassMapTest {

    private static final String RIPE = "RIPE";

    private IpResourceSet rs(String s) {
        return IpResourceSet.parse(s);
    }

    @Test
    public void should_return_empty_resources_for_unknown_resource_class() {
        ResourceClassMap resourceClassMap = empty();

        assertEquals(new IpResourceSet(), resourceClassMap.getResources(RIPE));
    }

    @Test
    public void should_be_immutable() {
        ResourceClassMap resourceClassMap = empty();
        ResourceClassMap resourcesWithInitialRipe = resourceClassMap.plus(RIPE, rs("2.0.0.0/8"));

        assertEquals("resource class map unmodified", empty(), resourceClassMap);
        assertNotSame(resourceClassMap, resourcesWithInitialRipe);
        assertNotSame(rs("2.0.0.0/8"), resourcesWithInitialRipe.getResources(RIPE));
    }

    @Test
    public void should_create_and_add_initial_resources_for_unknown_resource_class() {
        ResourceClassMap resourceClassMap = empty();
        ResourceClassMap resourcesWithInitialRipe = resourceClassMap.plus(RIPE, rs("2.0.0.0/8"));

        assertEquals(rs("2.0.0.0/8"), resourcesWithInitialRipe.getResources(RIPE));
    }

    @Test
    public void should_add_additional_resources_for_known_resource_class() {
        ResourceClassMap resourceClassMap = empty();
        ResourceClassMap resourcesWithInitialRipe = resourceClassMap.plus(RIPE, rs("2.0.0.0/8"));
        ResourceClassMap resourcesWithAdditionalRipe = resourcesWithInitialRipe.plus(RIPE, rs("3.0.0.0/8"));

        assertNotSame(resourcesWithInitialRipe, resourcesWithAdditionalRipe);
        assertEquals(rs("2.0.0.0/8, 3.0.0.0/8"), resourcesWithAdditionalRipe.getResources(RIPE));
    }

    @Test
    public void should_have_class_with_empty_resources() {
        assertEquals(Sets.newHashSet(RIPE), singleton(RIPE, rs("")).getClasses());
        assertEquals(Collections.<String>emptySet(), singleton(RIPE, rs("2.0.0.0/8")).minus(singleton(RIPE, rs("2.0.0.0/8"))).getClasses());
    }

    @Test
    public void should_be_empty_when_subtracted_from_itself() {
        ResourceClassMap example = singleton(RIPE, rs("2.0.0.0/8"));
        assertEquals(empty(), example.minus(example));
    }

    @Test
    public void should_remove_resources_per_resource_class() {
        ResourceClassMap a = singleton(RIPE, rs("2.0.0.0/8")).plus("ARIN", rs("10.0.0.0/8"));
        ResourceClassMap b = singleton("ARIN", rs("10.128.0.0/9"));

        ResourceClassMap expected = singleton(RIPE, rs("2.0.0.0/8")).plus("ARIN", rs("10.0.0.0/9"));
        assertEquals(expected, a.minus(b));
    }

    @Test
    public void should_ignore_resource_classes_not_in_own_when_subtracting() {
        ResourceClassMap a = singleton(RIPE, rs("2.0.0.0/8"));
        ResourceClassMap b = singleton("ARIN", rs("10.0.0.0/8"));

        assertEquals(a, a.minus(b));
    }

    @Test
    public void should_ignore_resources_not_in_own_when_subtracting() {
        ResourceClassMap a = singleton(RIPE, rs("2.0.0.0/8"));
        ResourceClassMap b = singleton(RIPE, rs("3.0.0.0/8"));

        assertEquals(a, a.minus(b));
    }

    @Test
    public void shouldFindResourceClassForResource() {
        ResourceClassMap resourceClassMap = singleton(RIPE, rs("2.0.0.0/8"));

        assertEquals(RIPE, resourceClassMap.findResourceClassContainingResourcesOrNull(rs("2.0.0.0/8")));
        assertNull(resourceClassMap.findResourceClassContainingResourcesOrNull(rs("3.0.0.0/8")));
    }

    @Test
    public void testContains() {
        assertTrue("empty contains empty", empty().contains(empty()));
        assertFalse("empty contains non-empty", empty().contains(singleton(RIPE, rs("2.0.0.0/8"))));

        assertTrue("non-empty contains empty", singleton(RIPE, rs("2.0.0.0/8")).contains(empty()));
        assertFalse("different resources do not contain other", singleton(RIPE, rs("2.0.0.0/8")).contains(singleton(RIPE, rs("3.0.0.0/8"))));
        assertFalse("different class does not contain other", singleton("ARIN", rs("2.0.0.0/8")).contains(singleton(RIPE, rs("2.0.0.0/8"))));

        assertFalse("subset of resources do not contain other", singleton(RIPE, rs("2.0.0.0/8")).contains(singleton(RIPE, rs("2.0.0.0/8, 3.0.0.0/8"))));
        assertTrue("superset of resources do contain other", singleton(RIPE, rs("2.0.0.0/8, 3.0.0.0/8")).contains(singleton(RIPE, rs("2.0.0.0/8"))));
    }
}
