package com.kartersanamo.havoc.base.lifecycle;

import com.kartersanamo.havoc.base.BaseDifficulty;
import org.junit.Assert;
import org.junit.Test;

public class SpawnPlannerTest {

    @Test
    public void queuedCountIncludesActiveAndPending() {
        SpawnPlanner planner = new SpawnPlanner();
        planner.enqueue(BaseDifficulty.EASY);
        planner.enqueue(BaseDifficulty.EASY);
        planner.enqueue(BaseDifficulty.HARD);

        planner.tick(new SpawnPlanner.SpawnTaskFactory() {
            @Override
            public SpawnPlanner.SpawnTask create(final BaseDifficulty difficulty) {
                return new SpawnPlanner.SpawnTask() {
                    @Override
                    public BaseDifficulty difficulty() {
                        return difficulty;
                    }

                    @Override
                    public boolean tick() {
                        return false; // keep active
                    }

                    @Override
                    public String phaseName() {
                        return "SEARCH";
                    }
                };
            }
        });

        Assert.assertEquals(2, planner.queuedCount(BaseDifficulty.EASY));
        Assert.assertEquals(1, planner.queuedCount(BaseDifficulty.HARD));
        Assert.assertEquals("EASY", planner.activeDifficultyName());
        Assert.assertEquals("SEARCH", planner.activePhaseName());
        Assert.assertEquals(3, planner.queuedTotal());
    }

    @Test
    public void activeClearsWhenTaskCompletes() {
        SpawnPlanner planner = new SpawnPlanner();
        planner.enqueue(BaseDifficulty.MEDIUM);

        planner.tick(new SpawnPlanner.SpawnTaskFactory() {
            @Override
            public SpawnPlanner.SpawnTask create(final BaseDifficulty difficulty) {
                return new SpawnPlanner.SpawnTask() {
                    @Override
                    public BaseDifficulty difficulty() {
                        return difficulty;
                    }

                    @Override
                    public boolean tick() {
                        return true; // complete immediately
                    }

                    @Override
                    public String phaseName() {
                        return "FINALIZE";
                    }
                };
            }
        });

        Assert.assertEquals("NONE", planner.activeDifficultyName());
        Assert.assertEquals("IDLE", planner.activePhaseName());
        Assert.assertEquals(0, planner.queuedTotal());
    }
}
