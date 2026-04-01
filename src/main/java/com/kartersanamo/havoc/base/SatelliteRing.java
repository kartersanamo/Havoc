package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.world.ChunkSnapshotUtil;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Snapshots of chunks around a base (excluding the 3x3 footprint) taken at breach time.
 * On cleanup we restore every snapshotted chunk and unclaim it (MVP: full ring reset).
 */
public final class SatelliteRing {

    private final String worldName;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int watchRadius;
    private final Map<ChunkKey, ChunkSnapshot> snapshots = new HashMap<ChunkKey, ChunkSnapshot>();

    private SatelliteRing(String worldName, int centerChunkX, int centerChunkZ, int watchRadius) {
        this.worldName = worldName;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.watchRadius = watchRadius;
    }

    public static SatelliteRing capture(World world, int centerChunkX, int centerChunkZ, int watchRadius) {
        SatelliteRing ring = new SatelliteRing(world.getName(), centerChunkX, centerChunkZ, watchRadius);
        for (int dcx = -watchRadius; dcx <= watchRadius; dcx++) {
            for (int dcz = -watchRadius; dcz <= watchRadius; dcz++) {
                if (Math.abs(dcx) <= 1 && Math.abs(dcz) <= 1) {
                    continue;
                }
                int cx = centerChunkX + dcx;
                int cz = centerChunkZ + dcz;
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load();
                }
                ring.snapshots.put(ChunkKey.of(ch), ChunkSnapshotUtil.snap(ch));
            }
        }
        return ring;
    }

    public boolean isInWatch(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        int dcx = cx - centerChunkX;
        int dcz = cz - centerChunkZ;
        if (Math.abs(dcx) <= 1 && Math.abs(dcz) <= 1) {
            return false;
        }
        return Math.max(Math.abs(dcx), Math.abs(dcz)) <= watchRadius;
    }

    public boolean isInFootprint(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        int dcx = cx - centerChunkX;
        int dcz = cz - centerChunkZ;
        return Math.abs(dcx) <= 1 && Math.abs(dcz) <= 1;
    }

    public void restoreAndUnclaimAll(World world, FactionsBridge factions) throws Exception {
        for (Map.Entry<ChunkKey, ChunkSnapshot> e : snapshots.entrySet()) {
            ChunkKey key = e.getKey();
            ChunkSnapshotUtil.restore(world, key.getX(), key.getZ(), e.getValue());
            factions.unclaimChunk(world.getChunkAt(key.getX(), key.getZ()));
        }
    }
}
