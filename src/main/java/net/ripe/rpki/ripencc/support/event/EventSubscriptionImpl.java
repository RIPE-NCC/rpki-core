package net.ripe.rpki.ripencc.support.event;

import org.apache.commons.lang.Validate;

import java.util.Collection;

class EventSubscriptionImpl<Listener> implements EventSubscription {

    private final Listener listener;
    private final Collection<? extends EventSubscription> subscriptions;

    public EventSubscriptionImpl(Listener listener, Collection<? extends EventSubscription> subscriptions) {
        Validate.notNull(listener, "listener is required");
        Validate.notNull(subscriptions, "subscriptions is required");
        this.listener = listener;
        this.subscriptions = subscriptions;
    }
    
    public Listener getListener() {
        return listener;
    }
    
    public void cancel() {
        subscriptions.remove(this);
    }

}
