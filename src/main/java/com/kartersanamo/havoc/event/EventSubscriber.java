package com.kartersanamo.havoc.event;

public interface EventSubscriber<T> {
    void onEvent(T event);
}
