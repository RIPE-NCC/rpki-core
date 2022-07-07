package net.ripe.rpki.ripencc.support.event;

import org.apache.commons.lang.Validate;

import java.util.Collection;

class EventSubscriptionImpl<L> implements EventSubscription, AutoCloseable {

    private final L listener;
    private final Collection<? extends EventSubscription> subscriptions;

    public EventSubscriptionImpl(L listener, Collection<? extends EventSubscription> subscriptions) {
        Validate.notNull(listener, "listener is required");
        Validate.notNull(subscriptions, "subscriptions is required");
        this.listener = listener;
        this.subscriptions = subscriptions;
    }
    
    public L getListener() {
        return listener;
    }

    @Override
    public void close() {
        subscriptions.remove(this);
    }
}
