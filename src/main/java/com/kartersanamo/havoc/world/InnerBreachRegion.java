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
 * Schematic-relative positions of the inner obsidian shell used for breach detection.
 * Nested shells are resolved by peeling blocks on the outermost obsidian AABB; the
 * remaining obsidian blocks form the inner breach surface.
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
        return new InnerBreachRegion(detectInnerShell(obsidian));
    }

    static InnerBreachRegion fromObsidianPositions(List<int[]> obsidian) {
        return new InnerBreachRegion(detectInnerShell(obsidian));
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

    private static Set<Long> detectInnerShell(List<int[]> obsidian) {
        if (obsidian.isEmpty()) {
            return new HashSet<Long>();
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] p : obsidian) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }

        Set<Long> outerShell = new HashSet<Long>();
        for (int[] p : obsidian) {
            if (isOnBounds(p[0], p[1], p[2], minX, minY, minZ, maxX, maxY, maxZ)) {
                outerShell.add(pack(p[0], p[1], p[2]));
            }
        }

        Set<Long> inner = new HashSet<Long>();
        for (int[] p : obsidian) {
            long key = pack(p[0], p[1], p[2]);
            if (!outerShell.contains(key)) {
                inner.add(key);
            }
        }
        if (!inner.isEmpty()) {
            return inner;
        }
        for (int[] p : obsidian) {
            inner.add(pack(p[0], p[1], p[2]));
        }
        return inner;
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
}
