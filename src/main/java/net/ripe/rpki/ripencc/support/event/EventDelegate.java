package net.ripe.rpki.ripencc.support.event;

/**
 * Interface used to register event listeners and to publish events to the
 * listeners.
 * 
 * @param <Event> the event type.
 * @param <Listener> the listener type.
 */
public interface EventDelegate<Event> {
    
    /**
     * Register a listener for events published in the current thread. The
     * listener <em>must</em> cancel the subscription when no longer
     * interested in the events. This is usually done with a try-finally block.
     * 
     * @param listener
     *            the listener
     * @return the registration that must be cancelled when no longer
     *         interested.
     */
    EventSubscription subscribe(EventListener<Event> listener);

    /**
     * Publish an event to all listeners that subscribed in the current thread.
     * 
     * @param publisher
     *            the publisher of the event.
     * @param event
     *            the event to publish.
     */
    void publish(Object publisher, Event event);

    /**
     * Cancel all subscriptions and reset the event delegate to the initial
     * state.
     */
    void reset();

}
