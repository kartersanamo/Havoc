package com.kartersanamo.havoc.generator;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;

import java.util.Arrays;

/**
 * Builds a {@link CuboidClipboard} from a {@link BaseTemplateDefinition}.
 * <p>
 * Layout: Y=0 stone floor full footprint; inner {@code sizeChunks * 16} region is hollow air from Y=1 up,
 * plus a roof at the top only over the inner main box.
 * Walls expand outward in Chebyshev (square) rings.
 * Flat walls alternate obsidian/water with outermost water.
 * Sand walls are unchanged in V1 (air gap, water, then sand shell).
 * Regen: air, lava, obsidian, water (outside to inside relative to base center).
 * Optional bottom slabs at Y=1 in gap/water columns to reduce sand falling.
 */
public final class BaseTemplateGenerator {

    private static final int STONE = BlockID.STONE;
    private static final int AIR = BlockID.AIR;
    private static final int STATIONARY_WATER = BlockID.STATIONARY_WATER;
    private static final int STATIONARY_LAVA = BlockID.STATIONARY_LAVA;
    private static final int OBSIDIAN = BlockID.OBSIDIAN;
    private static final int SAND = BlockID.SAND;
    /** Stone slab, bottom half (data 0). */
    private static final int STONE_SLAB_BOTTOM = 0;

    private BaseTemplateGenerator() {
    }

    public static BaseTemplateResult generate(BaseTemplateDefinition def, int wallHeightBlocks) {
        int innerBlocks = def.getSizeChunksOdd() * 16;
        int margin = def.totalThicknessBlocks();
        int w = innerBlocks + 2 * margin;
        int l = w;
        // Build full-height defensive shells by default (1.8 build limit is 256).
        int h = Math.max(256, Math.max(8, wallHeightBlocks));

        CuboidClipboard clip = new CuboidClipboard(new Vector(w, h, l));
        clip.setOrigin(new Vector(0, 0, 0));
        clip.setOffset(new Vector(0, 0, 0));

        fillSolid(clip, w, h, l, new BaseBlock(AIR));

        int ix0 = margin;
        int iz0 = margin;
        int ix1 = margin + innerBlocks - 1;
        int iz1 = margin + innerBlocks - 1;

        // Floor
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                clip.setBlock(new Vector(x, 0, z), new BaseBlock(STONE));
            }
        }

        // Hollow interior (loot room), leave roof layer for explicit cap.
        for (int x = ix0; x <= ix1; x++) {
            for (int z = iz0; z <= iz1; z++) {
                for (int y = 1; y < h - 1; y++) {
                    clip.setBlock(new Vector(x, y, z), new BaseBlock(AIR));
                }
            }
        }

        // Roof only over the main inner box.
        for (int x = ix0; x <= ix1; x++) {
            for (int z = iz0; z <= iz1; z++) {
                clip.setBlock(new Vector(x, h - 1, z), new BaseBlock(OBSIDIAN));
            }
        }

        boolean[] slabMask = new boolean[w * l];
        Arrays.fill(slabMask, false);

        int bx0 = ix0;
        int bx1 = ix1;
        int bz0 = iz0;
        int bz1 = iz1;

        for (DefenseSection section : def.getSections()) {
            for (int r = 0; r < section.getRepeats(); r++) {
                applyRing(clip, slabMask, w, l, h, bx0, bx1, bz0, bz1, section.getType());
                int t = section.getType().thicknessPerRepeat();
                bx0 -= t;
                bx1 += t;
                bz0 -= t;
                bz1 += t;
            }
        }

        if (def.isSlabFloorBetweenWalls()) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < l; z++) {
                    if (!slabMask[x + z * w]) {
                        continue;
                    }
                    if (x >= ix0 && x <= ix1 && z >= iz0 && z <= iz1) {
                        continue;
                    }
                    clip.setBlock(new Vector(x, 1, z), new BaseBlock(BlockID.STEP, STONE_SLAB_BOTTOM));
                }
            }
        }

        int anchorX = margin + innerBlocks / 2;
        int anchorZ = margin + innerBlocks / 2;
        int anchorY = Math.min(h - 2, 2 + Math.max(1, (h - 4) / 2));
        for (int y = 2; y < h - 1; y++) {
            clip.setBlock(new Vector(anchorX, y, anchorZ), new BaseBlock(OBSIDIAN));
        }

        return new BaseTemplateResult(clip, anchorX, anchorY, anchorZ);
    }

    private static void applyRing(CuboidClipboard clip, boolean[] slabMask, int w, int l, int h,
            int boxX0, int boxX1, int boxZ0, int boxZ1, DefenseType type) {
        int thickness = type.thicknessPerRepeat();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                int dist = chebyshevDistanceOutsideBox(x, z, boxX0, boxX1, boxZ0, boxZ1);
                if (dist < 1 || dist > thickness) {
                    continue;
                }
                BaseBlock columnBlock = ringBlock(type, dist, thickness);
                for (int y = 1; y < h; y++) {
                    clip.setBlock(new Vector(x, y, z), columnBlock);
                }
                if (type == DefenseType.FLAT_WALL || type == DefenseType.SAND_WALL) {
                    if (dist == 1 || dist == 2) {
                        slabMask[x + z * w] = true;
                    }
                } else if (type == DefenseType.REGEN_WALL) {
                    if (dist >= 1 && dist <= 3) {
                        slabMask[x + z * w] = true;
                    }
                }
            }
        }
    }

    /**
     * Distance from (x,z) to the axis-aligned box, Chebyshev metric. 0 if inside or on edge.
     */
    private static int chebyshevDistanceOutsideBox(int x, int z, int x0, int x1, int z0, int z1) {
        int nx = x;
        if (nx < x0) {
            nx = x0;
        } else if (nx > x1) {
            nx = x1;
        }
        int nz = z;
        if (nz < z0) {
            nz = z0;
        } else if (nz > z1) {
            nz = z1;
        }
        int dx = Math.abs(x - nx);
        int dz = Math.abs(z - nz);
        return Math.max(dx, dz);
    }

    /**
     * For outside cells, dist 1 is innermost (air gap), dist=thickness is outermost shell.
     */
    private static BaseBlock ringBlock(DefenseType type, int dist, int thickness) {
        switch (type) {
            case REGEN_WALL:
                // Outside (large dist): water; then obsidian; lava; air gap against inner box.
                if (dist == thickness) {
                    return new BaseBlock(STATIONARY_WATER);
                }
                if (dist == thickness - 1) {
                    return new BaseBlock(OBSIDIAN);
                }
                if (dist == thickness - 2) {
                    return new BaseBlock(STATIONARY_LAVA);
                }
                return new BaseBlock(AIR);
            case SAND_WALL:
                // Alternate from the inner edge outward:
                // dist=1 sand, dist=2 water, dist=3 sand, dist=4 water ...
                // This guarantees water between sand walls and at the outer edge.
                if ((dist % 2) == 1) {
                    return new BaseBlock(SAND);
                }
                return new BaseBlock(STATIONARY_WATER);
            case FLAT_WALL:
            default:
                // Alternate from the inner edge outward:
                // dist=1 obsidian, dist=2 water, dist=3 obsidian, dist=4 water ...
                // This guarantees water both between flat walls and at the very outside.
                if ((dist % 2) == 1) {
                    return new BaseBlock(OBSIDIAN);
                }
                return new BaseBlock(STATIONARY_WATER);
        }
    }

    private static void fillSolid(CuboidClipboard clip, int w, int h, int l, BaseBlock block) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < l; z++) {
                    clip.setBlock(new Vector(x, y, z), block);
                }
            }
        }
    }
}
