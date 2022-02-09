package net.ripe.rpki.util;

import lombok.SneakyThrows;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Allow only certain amount of actions happening in parallel.
 */
public class Bottleneck {

    private final Semaphore semaphore;

    public Bottleneck(int simultaneousRequests) {
        semaphore = new Semaphore(simultaneousRequests);
    }

    @SneakyThrows
    public <T> T call(Supplier<T> s) {
        semaphore.acquire();
        try {
            return s.get();
        } finally {
            semaphore.release();
        }
    }
}
