package net.ripe.rpki.util;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.Size;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


public class StreamsTest {

    @Property
    public void shouldGroup(@ForAll @Size(min= 3) List<Integer> s, @ForAll @Positive int chunk) {
        final Collection<List<Integer>> grouped = Streams.grouped(s, chunk);

        assertThat(grouped).hasSize((int) Math.ceil(s.size() / (double)chunk));

        grouped.stream().limit(grouped.size() - 1).forEach(g -> assertThat(g).hasSize(chunk));
        assertThat(grouped).last().satisfies(last -> assertThat(last.size()).isLessThanOrEqualTo(chunk));

        final List<Integer> concatenated = grouped.stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertThat(s).isEqualTo(concatenated);
    }
}
