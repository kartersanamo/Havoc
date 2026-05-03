package com.kartersanamo.havoc.storage;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AsyncPersistenceQueueTest {

    @Test
    public void executesTasksSerially() {
        AsyncPersistenceQueue queue = new AsyncPersistenceQueue();
        final List<Integer> out = Collections.synchronizedList(new ArrayList<Integer>());
        for (int i = 0; i < 50; i++) {
            final int n = i;
            queue.submit(new Runnable() {
                @Override
                public void run() {
                    out.add(n);
                }
            });
        }
        queue.shutdownAndDrain();

        Assert.assertEquals(50, out.size());
        for (int i = 0; i < 50; i++) {
            Assert.assertEquals(Integer.valueOf(i), out.get(i));
        }
    }
}
