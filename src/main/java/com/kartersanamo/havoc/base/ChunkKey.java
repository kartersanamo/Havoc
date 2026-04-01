package com.kartersanamo.havoc.base;

import org.bukkit.Chunk;
import org.bukkit.World;

public final class ChunkKey {

    private final String world;
    private final int x;
    private final int z;

    public ChunkKey(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkKey of(Chunk c) {
        return new ChunkKey(c.getWorld().getName(), c.getX(), c.getZ());
    }

    public static ChunkKey of(World w, int cx, int cz) {
        return new ChunkKey(w.getName(), cx, cz);
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChunkKey)) {
            return false;
        }
        ChunkKey chunkKey = (ChunkKey) o;
        return x == chunkKey.x && z == chunkKey.z && world.equals(chunkKey.world);
    }

    @Override
    public int hashCode() {
        int result = world.hashCode();
        result = 31 * result + x;
        result = 31 * result + z;
        return result;
    }
}
