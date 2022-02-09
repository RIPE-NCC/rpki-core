package net.ripe.rpki.util;

import java.util.function.Supplier;

public class Singleton<T> {

    private T value;
    private final Supplier<T> s;

    public Singleton(Supplier<T> s) {
        this.s = s;
    }

    public synchronized T get() {
        if (value == null) {
            value = s.get();
        }
        return value;
    }

    public static <T> Singleton<T> of(Supplier<T> s) {
        return new Singleton<>(s);
    }
}
