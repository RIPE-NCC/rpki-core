package net.ripe.rpki.ripencc.support.event;

public interface EventSubscription {

    /**
     * Stop listening to events for this event registration.
     */
    void cancel();

}
