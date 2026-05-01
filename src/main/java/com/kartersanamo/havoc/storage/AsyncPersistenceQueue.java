package com.kartersanamo.havoc.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AsyncPersistenceQueue {
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public void submit(Runnable task) {
        writer.submit(task);
    }

    public void shutdownAndDrain() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
