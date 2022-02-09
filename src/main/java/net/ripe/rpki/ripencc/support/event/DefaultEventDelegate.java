package net.ripe.rpki.ripencc.support.event;

import org.apache.commons.lang.Validate;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Default implementation of {@link EventDelegate} with support for multiple
 * subscribers. Events are dispatched synchronously through the
 * {@link EventDispatcher} provided at construction of an instance.
 * 
 * @param <Event>
 *            the event type.
 * @param <Listener>
 *            the subscription listener type.
 */
public class DefaultEventDelegate<Event> implements EventDelegate<Event> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventDelegate.class);

    private final ThreadLocal<List<EventSubscriptionImpl<EventListener<Event>>>> subscriptionsThreadLocal;

    public DefaultEventDelegate() {
        this.subscriptionsThreadLocal = ThreadLocal.withInitial(LinkedList::new);
    }

    public void publish(Object source, Event event) {
        Validate.notNull(source, "source is required");
        Validate.notNull(event, "event is required");
        for (EventSubscriptionImpl<EventListener<Event>> registration : subscriptionsThreadLocal.get()) {
            registration.getListener().notify(event);
        }
    }

    public EventSubscription subscribe(EventListener<Event> listener) {
        Validate.notNull(listener, "listener is required");
        EventDelegateTracker.get().register(this);
        final List<EventSubscriptionImpl<EventListener<Event>>> subscriptions = subscriptionsThreadLocal.get();
        EventSubscriptionImpl<EventListener<Event>> result = new EventSubscriptionImpl<>(listener, subscriptions);
        subscriptions.add(0, result);
        return result;
    }

    public void reset() {
        List<EventSubscriptionImpl<EventListener<Event>>> subscriptions = subscriptionsThreadLocal.get();
        if (!subscriptions.isEmpty()) {
            LOG.warn("not all subscriptions were cancelled before reset of EventDelegate");
        }
        subscriptionsThreadLocal.remove();
    }

}
