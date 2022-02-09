package net.ripe.rpki.ripencc.support.event;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;


public class EventDelegateTrackerTest {

    private List<String> events = new ArrayList<>();
    
    private final class TestEventListener implements EventListener<String> {
        public void notify(String event) {
            events.add(event);
        }
    }
    @Test
    public void shouldResetAllRegisteredEventDelegates() {
        DefaultEventDelegate<String> delegate1 = new DefaultEventDelegate<>();
        DefaultEventDelegate<String> delegate2 = new DefaultEventDelegate<>();
        
        delegate1.subscribe(new TestEventListener());
        delegate2.subscribe(new TestEventListener());
        
        delegate1.publish(this, "one");
        delegate2.publish(this, "two");
        assertEquals(Arrays.asList("one", "two"), events);
        
        EventDelegateTracker.get().reset();
        
        events.clear();
        delegate1.publish(this, "one");
        delegate2.publish(this, "two");
        assertEquals(Collections.emptyList(), events);
    }

}
