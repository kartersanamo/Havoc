package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.world.ColumnBoxSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActiveHavocBase {

    public final UUID id = UUID.randomUUID();
    public final BaseDifficulty difficulty;
    public final String worldName;
    /** Chunk containing the obsidian core (set after paste from real world coords). */
    public int centerChunkX;
    public int centerChunkZ;

    /** Actual WorldEdit paste corner (clipboard min block in world). */
    public int pasteOriginX;
    public int pasteOriginY;
    public int pasteOriginZ;
    public int footprintSizeX;
    public int footprintSizeZ;
    public int chunkFootprintRadius;

    public int obsidianCenterX;
    public int obsidianCenterY;
    public int obsidianCenterZ;

    public final List<ChunkKey> claimedChunks = new ArrayList<ChunkKey>();

    public BaseState state = BaseState.ACTIVE;
    public ColumnBoxSnapshot terrainSnapshot;
    public int restoreCursor;
    public SatelliteRing satellite;
    public long raidEndMs;
    public int satelliteTaskId = -1;

    public ActiveHavocBase(BaseDifficulty difficulty, String worldName) {
        this.difficulty = difficulty;
        this.worldName = worldName;
    }

    public boolean containsBlockColumn(int bx, int bz) {
        return bx >= pasteOriginX && bx < pasteOriginX + footprintSizeX
                && bz >= pasteOriginZ && bz < pasteOriginZ + footprintSizeZ;
    }

    public boolean isCenterChunk(org.bukkit.Chunk c) {
        return c.getX() == centerChunkX && c.getZ() == centerChunkZ;
    }
}
