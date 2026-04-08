package com.kartersanamo.havoc.world;

import com.sk89q.worldedit.CuboidClipboard;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Helpers for aligning schematics that were saved with a shifted player origin.
 */
public final class SchematicPlacement {

    private SchematicPlacement() {
    }

    /**
     * Lowest clipboard Y row (0 .. height-1) that contains any non-air block.
     */
    public static int lowestSolidY(CuboidClipboard clip) {
        return SchematicAnalysis.analyze(clip).lowestNonAirY;
    }

    /**
     * Highest Y in the column that is still bedrock (vanilla bedrock pillar).
     */
    public static int highestBedrockY(World world, int bx, int bz) {
        int top = -1;
        for (int y = 0; y < world.getMaxHeight(); y++) {
            if (world.getBlockAt(bx, y, bz).getType() == Material.BEDROCK) {
                top = y;
            }
        }
        return top;
    }
}
