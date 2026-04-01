package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.world.ColumnBoxSnapshot;

import java.util.UUID;

public final class ActiveHavocBase {

    public final UUID id = UUID.randomUUID();
    public final BaseDifficulty difficulty;
    public final String worldName;
    public final int centerChunkX;
    public final int centerChunkZ;
    public final int minBlockX;
    public final int minBlockZ;
    public final int footprintSize = 48;

    public BaseState state = BaseState.ACTIVE;
    public ColumnBoxSnapshot terrainSnapshot;
    public int restoreCursor;
    public SatelliteRing satellite;
    public long raidEndMs;

    public ActiveHavocBase(BaseDifficulty difficulty, String worldName, int centerChunkX, int centerChunkZ) {
        this.difficulty = difficulty;
        this.worldName = worldName;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.minBlockX = (centerChunkX - 1) * 16;
        this.minBlockZ = (centerChunkZ - 1) * 16;
    }

    public boolean containsBlockColumn(int bx, int bz) {
        return bx >= minBlockX && bx < minBlockX + footprintSize && bz >= minBlockZ && bz < minBlockZ + footprintSize;
    }

    public boolean isCenterChunk(org.bukkit.Chunk c) {
        return c.getX() == centerChunkX && c.getZ() == centerChunkZ;
    }

    public void registerChunks(java.util.function.BiConsumer<Integer, Integer> chunkConsumer) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunkConsumer.accept(centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }
}
