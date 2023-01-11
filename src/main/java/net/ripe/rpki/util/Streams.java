package net.ripe.rpki.util;

import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Streams {

    private Streams() {
    }

    public static <T> Collection<List<T>> grouped(final Stream<T> s, final int chunkSize) {
        final AtomicInteger counter = new AtomicInteger(0);
        return s.collect(Collectors.groupingBy(it -> counter.getAndIncrement() / chunkSize)).values();
    }

    public static <T> Collection<List<T>> grouped(final List<T> s, final int chunkSize) {
        return grouped(s.stream(), chunkSize);
    }

    @SneakyThrows
    public static String entityTag(Stream<byte[]> data) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        data.forEachOrdered(digest::update);
        return "\"" + Base64.getEncoder().encodeToString(digest.digest()) + "\"";
    }

    public static <T, K, V> SortedMap<K, V> streamToSortedMap(Stream<T> stream, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return stream.collect(toSortedMap(keyMapper, valueMapper));
    }

    public static <T, K, V> Collector<T, ?, TreeMap<K, V>> toSortedMap(Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper, throwingMerger(), TreeMap::new);
    }

    /** Copied from java.util.Collectors, where it is private */
    public static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }
}
