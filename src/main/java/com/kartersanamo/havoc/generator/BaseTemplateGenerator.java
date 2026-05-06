package com.kartersanamo.havoc.generator;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;

import java.util.Arrays;

/**
 * Builds a {@link CuboidClipboard} from a {@link BaseTemplateDefinition}.
 * <p>
 * Layout: Y=0 is left untouched by generated solids; inner {@code sizeChunks * 16} region is hollow air from Y=1 up,
 * plus a roof at the top only over the inner main box.
 * Walls expand outward in Chebyshev (square) rings.
 * Flat walls alternate obsidian/water with outermost water.
 * Sand walls are unchanged in V1 (air gap, water, then sand shell).
 * Regen: air, lava, obsidian, water (outside to inside relative to base center).
 * Optional bottom slabs at Y=1 in gap/water columns to reduce sand falling.
 */
public final class BaseTemplateGenerator {

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
                    BaseBlock existing = clip.getBlock(new Vector(x, 1, z));
                    if (existing == null || !existing.isAir()) {
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
        if (type == DefenseType.REGEN_WALL) {
            applyRegenCardinalBands(clip, slabMask, w, l, h, boxX0, boxX1, boxZ0, boxZ1);
            return;
        }
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
     * Regen walls are generated only on the 4 cardinal fronts of the base footprint,
     * not as a full square ring through corners.
     *
     * Thickness (inside -> outside):
     * 1: OBSIDIAN
     * 2: alternating LAVA / OBSIDIAN pillars across the full side width
     * 3: OBSIDIAN
     * 4: WATER (outer wash)
     */
    private static void applyRegenCardinalBands(CuboidClipboard clip, boolean[] slabMask, int w, int l, int h,
            int boxX0, int boxX1, int boxZ0, int boxZ1) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                int northDist = boxZ0 - z;
                int southDist = z - boxZ1;
                int westDist = boxX0 - x;
                int eastDist = x - boxX1;

                boolean inXSpan = x >= boxX0 && x <= boxX1;
                boolean inZSpan = z >= boxZ0 && z <= boxZ1;

                int dist = 0;
                int axisKey = 0;
                if (northDist >= 1 && northDist <= 4 && inXSpan) {
                    dist = northDist;
                    axisKey = x;
                } else if (southDist >= 1 && southDist <= 4 && inXSpan) {
                    dist = southDist;
                    axisKey = x;
                } else if (westDist >= 1 && westDist <= 4 && inZSpan) {
                    dist = westDist;
                    axisKey = z;
                } else if (eastDist >= 1 && eastDist <= 4 && inZSpan) {
                    dist = eastDist;
                    axisKey = z;
                } else {
                    continue;
                }

                BaseBlock block;
                if (dist == 4) {
                    block = new BaseBlock(STATIONARY_WATER);
                } else if (dist == 3 || dist == 1) {
                    block = new BaseBlock(OBSIDIAN);
                } else {
                    // dist == 2: repeating lava cores with obsidian separators/wrap.
                    block = ((axisKey & 1) == 0) ? new BaseBlock(STATIONARY_LAVA) : new BaseBlock(OBSIDIAN);
                }

                for (int y = 1; y < h; y++) {
                    clip.setBlock(new Vector(x, y, z), block);
                }

                // Slab support only marks the innermost air-adjacent gap lane.
                if (dist == 1) {
                    slabMask[x + z * w] = true;
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
