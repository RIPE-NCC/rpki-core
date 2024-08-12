package net.ripe.rpki.util;

import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
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

    public static <T> Predicate<T> distinctByKey(
            Function<? super T, ?> keyExtractor) {

        var seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
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

    public static <T, K, V> Collector<T, ?, SortedMap<K, V>> toSortedMap(Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return toSortedMap(keyMapper, valueMapper, null);
    }

    public static <T, K, V> Collector<T, ?, SortedMap<K, V>> toSortedMap(Function<T, K> keyMapper, Function<T, V> valueMapper, Comparator<? super K> comparator) {
        return Collectors.toMap(keyMapper, valueMapper, throwingMerger(), () -> new TreeMap<>(comparator));
    }

    /** Copied from java.util.Collectors, where it is private */
    public static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }
}
