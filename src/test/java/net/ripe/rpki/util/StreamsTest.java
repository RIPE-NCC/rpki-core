package net.ripe.rpki.util;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.Size;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


public class StreamsTest {

    @Property
    public void shouldGroup(@ForAll @Size(min= 3) List<Integer> s, @ForAll @Positive int chunk) {
        final Collection<List<Integer>> grouped = Streams.grouped(s, chunk);

        assertThat(grouped).hasSize((int) Math.ceil(s.size() / (double)chunk));

        grouped.stream().limit(grouped.size() - 1).forEach(g -> assertThat(g).hasSize(chunk));
        assertThat(grouped).last().satisfies(last -> assertThat(last.size()).isLessThanOrEqualTo(chunk));

        final List<Integer> concatenated = grouped.stream().flatMap(Collection::stream).toList();
        assertThat(s).isEqualTo(concatenated);
    }

    @Test
    void shouldFilterDistinctByKey_random() {
        var uniqueStrings = IntStream.range(0, 26).mapToObj(String::valueOf).collect(Collectors.toList());;
        Collections.shuffle(uniqueStrings);
        var duplicated = Stream.concat(uniqueStrings.stream(), uniqueStrings.stream()).collect(Collectors.toList());

        List<String> distinct = duplicated.stream().filter(Streams.distinctByKey(Function.identity())).collect(Collectors.toList());
        assertThat(distinct).containsExactlyInAnyOrderElementsOf(uniqueStrings);
    }

    @Test
    void shouldFilterDistinctByKey_tuple() {
        var inputs = List.of(Pair.of("A", 9), Pair.of("Z", 1), Pair.of("Y", 1));

        // Rights are not unique
        assertThat(inputs.stream().filter(Streams.distinctByKey(Pair::getRight))).hasSize(2);

        // Lefts are
        assertThat(inputs.stream().filter(Streams.distinctByKey(Pair::getLeft))).hasSize(3);

        // As are the objects
        assertThat(inputs.stream().filter(Streams.distinctByKey(Function.identity()))).hasSize(3);
    }
}
