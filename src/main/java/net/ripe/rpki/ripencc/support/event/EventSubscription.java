package net.ripe.rpki.ripencc.support.event;

public interface EventSubscription extends AutoCloseable {

    /**
     * Stop listening to events for this event registration.
     */
    void close();

}
