package net.ripe.rpki.ripencc.support.event;

public interface EventListener<Event> {

    void notify(Event event);
    
}
