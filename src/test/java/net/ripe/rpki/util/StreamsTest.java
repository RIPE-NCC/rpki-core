package net.ripe.rpki.util;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;


@RunWith(JUnitQuickcheck.class)
public class StreamsTest {

    @Property
    public void shouldGroup(List<Integer> s, int chunk) {
        assumeThat(chunk, greaterThan(0));
        final Collection<List<Integer>> grouped = Streams.grouped(s, chunk);
        grouped.forEach(g -> assertTrue(g.size() <= chunk));
        final List<Integer> r = grouped.stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals(r, s);
    }
}
