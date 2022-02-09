package net.ripe.rpki.ripencc.support.event;

/**
 * Implementations are responsible for dispatching the event to the listener.
 * 
 * @param <Event> the event type.
 * @param <Listener> the listener type.
 */
public interface EventDispatcher<Event, Listener> {

    void dispatch(Event event, Listener listener);
    
}
