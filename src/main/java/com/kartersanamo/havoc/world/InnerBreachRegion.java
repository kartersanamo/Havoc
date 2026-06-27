package com.kartersanamo.havoc.world;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Schematic-relative positions of the innermost obsidian shell used for breach detection.
 */
public final class InnerBreachRegion {

    private static final int OBSIDIAN_TYPE_ID = Material.OBSIDIAN.getId();
    private final Set<Long> packedRelativeBlocks;

    private InnerBreachRegion(Set<Long> packedRelativeBlocks) {
        this.packedRelativeBlocks = packedRelativeBlocks;
    }

    public static InnerBreachRegion empty() {
        return new InnerBreachRegion(new HashSet<Long>());
    }

    public boolean isEmpty() {
        return packedRelativeBlocks.isEmpty();
    }

    public int size() {
        return packedRelativeBlocks.size();
    }

    public boolean containsWorldBlock(int worldX, int worldY, int worldZ, int originX, int originY, int originZ) {
        return packedRelativeBlocks.contains(pack(worldX - originX, worldY - originY, worldZ - originZ));
    }

    public static InnerBreachRegion fromSchematic(CuboidClipboard clip) {
        List<int[]> obsidian = collectObsidian(clip);
        return new InnerBreachRegion(detectInnermostShell(obsidian));
    }

    static InnerBreachRegion fromObsidianPositions(List<int[]> obsidian) {
        return new InnerBreachRegion(detectInnermostShell(obsidian));
    }

    private static List<int[]> collectObsidian(CuboidClipboard clip) {
        int w = clip.getWidth();
        int h = clip.getHeight();
        int len = clip.getLength();
        List<int[]> out = new ArrayList<int[]>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < len; z++) {
                    BaseBlock bb;
                    try {
                        bb = clip.getBlock(new Vector(x, y, z));
                    } catch (Exception e) {
                        continue;
                    }
                    if (isObsidian(bb)) {
                        out.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return out;
    }

    private static Set<Long> detectInnermostShell(List<int[]> obsidian) {
        if (obsidian.isEmpty()) {
            return new HashSet<Long>();
        }
        Set<Long> remaining = new HashSet<Long>();
        for (int[] p : obsidian) {
            remaining.add(pack(p[0], p[1], p[2]));
        }
        while (!remaining.isEmpty()) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long key : remaining) {
                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }

            Set<Long> interior = new HashSet<Long>();
            for (long key : remaining) {
                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                if (!isOnBounds(x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                    interior.add(key);
                }
            }
            if (interior.isEmpty()) {
                return remaining;
            }
            remaining = interior;
        }
        return remaining;
    }

    private static boolean isOnBounds(int x, int y, int z,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
    }

    private static boolean isObsidian(BaseBlock bb) {
        return bb != null && !bb.isAir() && bb.getType() == OBSIDIAN_TYPE_ID;
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFF) << 36) | ((long) (y & 0xFFF) << 24) | (z & 0xFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) ((packed >> 36) & 0x3FFFF);
    }

    private static int unpackY(long packed) {
        return (int) ((packed >> 24) & 0xFFF);
    }

    private static int unpackZ(long packed) {
        return (int) (packed & 0xFFFFFFL);
    }
}
