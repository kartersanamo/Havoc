package com.kartersanamo.havoc.world;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

public final class ChunkSnapshotUtil {

    private ChunkSnapshotUtil() {
    }

    public static ChunkSnapshot snap(Chunk chunk) {
        return chunk.getChunkSnapshot(false, false, false);
    }

    public static void restore(World world, int chunkX, int chunkZ, ChunkSnapshot snap) {
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        int h = world.getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < h; y++) {
                    int id = snap.getBlockTypeId(x, y, z);
                    int data = snap.getBlockData(x, y, z);
                    int wx = chunkX * 16 + x;
                    int wz = chunkZ * 16 + z;
                    world.getBlockAt(wx, y, wz).setTypeIdAndData(id, (byte) data, false);
                }
            }
        }
    }
}
