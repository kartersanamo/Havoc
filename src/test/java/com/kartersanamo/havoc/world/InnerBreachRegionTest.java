package com.kartersanamo.havoc.world;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class InnerBreachRegionTest {

    @Test
    public void nestedShellUsesInnerObsidianOnly() {
        List<int[]> obsidian = new ArrayList<int[]>();
        addSolidHollowCube(obsidian, 0, 10);
        addSolidHollowCube(obsidian, 3, 7);

        InnerBreachRegion region = InnerBreachRegion.fromObsidianPositions(obsidian);

        Assert.assertTrue(region.containsWorldBlock(4, 4, 3, 0, 0, 0));
        Assert.assertTrue(region.containsWorldBlock(3, 5, 5, 0, 0, 0));
        Assert.assertFalse(region.containsWorldBlock(0, 5, 5, 0, 0, 0));
        Assert.assertFalse(region.containsWorldBlock(10, 5, 5, 0, 0, 0));
    }

    @Test
    public void singleShellFallsBackToAllObsidian() {
        List<int[]> obsidian = new ArrayList<int[]>();
        addSolidHollowCube(obsidian, 0, 6);

        InnerBreachRegion region = InnerBreachRegion.fromObsidianPositions(obsidian);

        Assert.assertTrue(region.containsWorldBlock(0, 3, 3, 0, 0, 0));
        Assert.assertTrue(region.containsWorldBlock(6, 3, 3, 0, 0, 0));
    }

    private static void addSolidHollowCube(List<int[]> out, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int y = min; y <= max; y++) {
                for (int z = min; z <= max; z++) {
                    boolean onFace = x == min || x == max || y == min || y == max || z == min || z == max;
                    if (onFace) {
                        out.add(new int[]{x, y, z});
                    }
                }
            }
        }
    }
}
