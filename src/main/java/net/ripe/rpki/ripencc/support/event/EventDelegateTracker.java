package net.ripe.rpki.ripencc.support.event;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks all {@link EventDelegate}s used in the current thread. This allows
 * resetting every event delegate when the thread exists the application code
 * (usually the application service boundary). Otherwise subscriptions may
 * easily leak into other application threads due to the application server
 * thread pool mechanism.
 *
 * NOTE: in the future we may want to support nested scoping (push/pop) instead
 * of just thread scoping to allow different layers of the application to use
 * this event mechanism independently. This would be similar to transaction
 * nesting where the outer transaction is suspended until the inner transaction
 * ends.
 */
public final class EventDelegateTracker {

    private static ThreadLocal<EventDelegateTracker> trackers = ThreadLocal.withInitial(EventDelegateTracker::new);

    public static EventDelegateTracker get() {
        return trackers.get();
    }

    /*
     * Map used as a set, since there is no WeakHashSet implementation.
     */
    private Map<EventDelegate<?>, Object> eventDelegates = new WeakHashMap<>();

    private EventDelegateTracker() {
    }

    public void register(EventDelegate<?> eventDelegate) {
        eventDelegates.put(eventDelegate, null);
    }

    public void reset() {
        for (Map.Entry<EventDelegate<?>, Object> eventDelegate : eventDelegates.entrySet()) {
            eventDelegate.getKey().reset();
        }
        trackers.remove();
    }

}
