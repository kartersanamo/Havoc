package com.kartersanamo.havoc.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InternalEventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<EventSubscriber<?>>> listeners =
            new ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventSubscriber<?>>>();

    public <T> void register(Class<T> eventType, EventSubscriber<T> subscriber) {
        CopyOnWriteArrayList<EventSubscriber<?>> subs = listeners.get(eventType);
        if (subs == null) {
            subs = new CopyOnWriteArrayList<EventSubscriber<?>>();
            listeners.put(eventType, subs);
        }
        subs.add(subscriber);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        if (event == null) {
            return;
        }
        List<EventSubscriber<?>> subs = listeners.get(event.getClass());
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (EventSubscriber<?> raw : subs) {
            try {
                ((EventSubscriber<T>) raw).onEvent(event);
            } catch (Throwable ignored) {
            }
        }
    }
}
