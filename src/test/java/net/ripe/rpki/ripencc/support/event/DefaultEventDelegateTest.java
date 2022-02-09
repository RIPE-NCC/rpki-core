package net.ripe.rpki.ripencc.support.event;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DefaultEventDelegateTest {

    private EventDelegate<String> subject = new DefaultEventDelegate<>();
    private List<String> events = new ArrayList<>();
    
    private final class TestEventListener implements EventListener<String> {
        public void notify(String event) {
            events.add(event);
        }
    }

    @Test
    public void shouldTrackSubscriptions() {
        EventSubscription sub = subject.subscribe(new TestEventListener());
        subject.publish(this, "hello world");
        assertEquals(Collections.singletonList("hello world"), events);
        
        sub.cancel();

        events.clear();
        subject.publish(this, "hallo wereld");
        assertEquals(Collections.<String>emptyList(), events);
    }

    @Test
    public void shouldSupportMultipleSubscriptions() {
        EventSubscription sub1 = subject.subscribe(new TestEventListener());
        EventSubscription sub2 = subject.subscribe(new TestEventListener());
        subject.publish(this, "hello world");
        assertEquals(Arrays.asList("hello world", "hello world"), events);

        sub1.cancel();
        sub2.cancel();

        events.clear();
        subject.publish(this, "hallo wereld");
        assertEquals(Collections.<String>emptyList(), events);
    }

    @Test
    public void shouldClearAllSubscriptionsOnReset() {
        subject.subscribe(new TestEventListener());
        subject.subscribe(new TestEventListener());
        
        subject.reset();
        
        events.clear();
        subject.publish(this, "hallo wereld");
        assertEquals(Collections.<String>emptyList(), events);
    }

}
