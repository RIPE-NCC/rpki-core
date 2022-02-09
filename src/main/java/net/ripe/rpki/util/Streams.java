package net.ripe.rpki.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Streams {

    public static <T> Collection<List<T>> grouped(final Stream<T> s, final int chunkSize) {
        final AtomicInteger counter = new AtomicInteger(0);
        return s.collect(Collectors.groupingBy(it -> counter.getAndIncrement() / chunkSize)).values();
    }

    public static <T> Collection<List<T>> grouped(final List<T> s, final int chunkSize) {
        return grouped(s.stream(), chunkSize);
    }

    public static <T, R> List<R> mapList(List<T> t, Function<T, R> f) {
        return t.stream().map(f).collect(Collectors.toList());
    }
}
