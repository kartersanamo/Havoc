package com.kartersanamo.havoc.world;

import com.kartersanamo.havoc.base.ChunkKey;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;

import java.util.Set;

/**
 * Single pass over clipboard indices (0..width-1, etc.): vertical solid bounds and per-column footprint for claims.
 */
public final class SchematicAnalysis {

    public final int width;
    public final int height;
    public final int length;
    public final int lowestNonAirY;
    public final int highestNonAirY;
    private final boolean[] columnHasBlock;

    private SchematicAnalysis(int width, int height, int length, int lowestNonAirY, int highestNonAirY, boolean[] columnHasBlock) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.lowestNonAirY = lowestNonAirY;
        this.highestNonAirY = highestNonAirY;
        this.columnHasBlock = columnHasBlock;
    }

    public static SchematicAnalysis analyze(CuboidClipboard clip) {
        int w = clip.getWidth();
        int h = clip.getHeight();
        int len = clip.getLength();
        boolean[] col = new boolean[w * len];
        int minY = h;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < len; z++) {
                    BaseBlock bb;
                    try {
                        bb = clip.getBlock(new Vector(x, y, z));
                    } catch (Exception e) {
                        continue;
                    }
                    if (bb == null || bb.isAir()) {
                        continue;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    col[x + z * w] = true;
                }
            }
        }
        if (minY == h) {
            minY = 0;
            maxY = 0;
        }
        return new SchematicAnalysis(w, h, len, minY, maxY, col);
    }

    public boolean columnHasBlock(int sx, int sz) {
        return columnHasBlock[sx + sz * width];
    }

    /**
     * Faction chunks: every chunk that contains a non-air column when the clipboard min is at {@code originX}, {@code originZ}.
     */
    public void collectClaimChunks(String worldName, int originX, int originZ, Set<ChunkKey> out) {
        for (int sx = 0; sx < width; sx++) {
            for (int sz = 0; sz < length; sz++) {
                if (!columnHasBlock(sx, sz)) {
                    continue;
                }
                int wx = originX + sx;
                int wz = originZ + sz;
                out.add(new ChunkKey(worldName, Math.floorDiv(wx, 16), Math.floorDiv(wz, 16)));
            }
        }
    }

    /** Ensures the breach anchor column's chunk is claimed even if that column is empty in the file. */
    public void ensureAnchorChunkClaimed(String worldName, int originX, int originZ, int anchorSx, int anchorSz, Set<ChunkKey> out) {
        int wx = originX + anchorSx;
        int wz = originZ + anchorSz;
        out.add(new ChunkKey(worldName, Math.floorDiv(wx, 16), Math.floorDiv(wz, 16)));
    }
}
