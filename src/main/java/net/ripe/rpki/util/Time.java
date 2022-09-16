package net.ripe.rpki.util;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Time {
    @Value
    @AllArgsConstructor
    public static class Timed<T> {
        T result;
        long time;
    }

    public static <T> Timed<T> timed(Supplier<T> s) {
        long begin = System.nanoTime();
        T t = s.get();
        long end = System.nanoTime();
        return new Timed<>(t, ((end - begin) + 500000) / 1000000);
    }

    public static long timed(Runnable s) {
        return timed(() -> {
            s.run();
            return null;
        }).getTime();
    }

    public static String formatDuration(Duration duration) {
        if (duration.compareTo(Duration.ofSeconds(1)) < 0) {
            return "0 seconds";
        }
        return Arrays.stream(new String[] {
                formatDurationField(duration.toDays() % 365, "day"),
                formatDurationField(duration.toHours() % 24, "hour"),
                formatDurationField(duration.toMinutes() % 60, "minute"),
                formatDurationField(duration.getSeconds() % 60, "second")
            })
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(" "));
    }

    private static String formatDurationField(long value, String singularSuffix) {
        if (value == 0) {
            return "";
        } else if (value == 1) {
            return value + " " + singularSuffix;
        } else {
            return value + " " + singularSuffix + "s";
        }
    }
}