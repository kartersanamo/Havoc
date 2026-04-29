package com.kartersanamo.havoc.base.lifecycle;

import com.kartersanamo.havoc.base.BaseDifficulty;

import java.util.ArrayDeque;

public final class SpawnPlanner {

    public interface SpawnTask {
        BaseDifficulty difficulty();
        boolean tick();
        String phaseName();
    }

    public interface SpawnTaskFactory {
        SpawnTask create(BaseDifficulty difficulty);
    }

    private final ArrayDeque<BaseDifficulty> queue = new ArrayDeque<BaseDifficulty>();
    private SpawnTask active;
    private boolean inFlight;

    public void enqueue(BaseDifficulty difficulty) {
        queue.addLast(difficulty);
    }

    public int queuedCount(BaseDifficulty difficulty) {
        int n = 0;
        if (active != null && active.difficulty() == difficulty) {
            n++;
        }
        for (BaseDifficulty q : queue) {
            if (q == difficulty) {
                n++;
            }
        }
        return n;
    }

    public void tick(SpawnTaskFactory factory) {
        if (inFlight) {
            return;
        }
        if (active == null) {
            BaseDifficulty next = queue.pollFirst();
            if (next == null) {
                return;
            }
            active = factory.create(next);
        }
        inFlight = true;
        try {
            if (active.tick()) {
                active = null;
            }
        } finally {
            inFlight = false;
        }
    }

    public int queuedTotal() {
        return queue.size() + (active == null ? 0 : 1);
    }

    public String activePhaseName() {
        return active == null ? "IDLE" : active.phaseName();
    }

    public String activeDifficultyName() {
        return active == null ? "NONE" : active.difficulty().name();
    }
}
