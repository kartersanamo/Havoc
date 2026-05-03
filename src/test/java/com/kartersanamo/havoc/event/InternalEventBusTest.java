package com.kartersanamo.havoc.event;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class InternalEventBusTest {

    @Test
    public void publishesToRegisteredSubscribersInOrder() {
        InternalEventBus bus = new InternalEventBus();
        final List<String> out = new ArrayList<String>();
        bus.register(String.class, new EventSubscriber<String>() {
            @Override
            public void onEvent(String event) {
                out.add("a:" + event);
            }
        });
        bus.register(String.class, new EventSubscriber<String>() {
            @Override
            public void onEvent(String event) {
                out.add("b:" + event);
            }
        });

        bus.publish("x");

        Assert.assertEquals(2, out.size());
        Assert.assertEquals("a:x", out.get(0));
        Assert.assertEquals("b:x", out.get(1));
    }

    @Test
    public void isolatesSubscriberExceptions() {
        InternalEventBus bus = new InternalEventBus();
        final List<String> out = new ArrayList<String>();
        bus.register(Integer.class, new EventSubscriber<Integer>() {
            @Override
            public void onEvent(Integer event) {
                throw new RuntimeException("boom");
            }
        });
        bus.register(Integer.class, new EventSubscriber<Integer>() {
            @Override
            public void onEvent(Integer event) {
                out.add(String.valueOf(event));
            }
        });

        bus.publish(42);

        Assert.assertEquals(1, out.size());
        Assert.assertEquals("42", out.get(0));
    }

    @Test
    public void ignoresNullAndUnregisteredEvents() {
        InternalEventBus bus = new InternalEventBus();
        bus.publish(null);
        bus.publish("none");
    }
}
