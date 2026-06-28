package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.world.ChunkSnapshotUtil;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-raid chunk snapshots for every chunk that can be affected by a base (claims + watch radius),
 * plus any extra chunks touched during the raid. Restored to this state on cleanup so cannons and
 * other raid builds are removed.
 */
public final class SatelliteRing {

    private final String worldName;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int watchRadius;
    private final Map<ChunkKey, ChunkSnapshot> snapshots = new ConcurrentHashMap<ChunkKey, ChunkSnapshot>();

    private SatelliteRing(String worldName, int centerChunkX, int centerChunkZ, int watchRadius) {
        this.worldName = worldName;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.watchRadius = watchRadius;
    }

    public static SatelliteRing captureAtSpawn(World world, int centerChunkX, int centerChunkZ, int watchRadius,
            Collection<ChunkKey> claimedChunks) {
        SatelliteRing ring = new SatelliteRing(world.getName(), centerChunkX, centerChunkZ, watchRadius);
        Set<ChunkKey> keys = new HashSet<ChunkKey>();
        if (claimedChunks != null) {
            for (ChunkKey key : claimedChunks) {
                if (key != null && world.getName().equals(key.getWorld())) {
                    keys.add(key);
                }
            }
        }
        for (int dcx = -watchRadius; dcx <= watchRadius; dcx++) {
            for (int dcz = -watchRadius; dcz <= watchRadius; dcz++) {
                if (Math.max(Math.abs(dcx), Math.abs(dcz)) > watchRadius) {
                    continue;
                }
                keys.add(new ChunkKey(world.getName(), centerChunkX + dcx, centerChunkZ + dcz));
            }
        }
        for (ChunkKey key : keys) {
            ring.snapshotChunk(world, key);
        }
        return ring;
    }

    public void ensureSnapshotBeforeChange(World world, Chunk chunk) {
        if (world == null || chunk == null || !world.getName().equals(worldName)) {
            return;
        }
        ChunkKey key = ChunkKey.of(chunk);
        if (snapshots.containsKey(key)) {
            return;
        }
        snapshotChunk(world, key);
    }

    private void snapshotChunk(World world, ChunkKey key) {
        Chunk ch = world.getChunkAt(key.getX(), key.getZ());
        if (!ch.isLoaded()) {
            ch.load();
        }
        snapshots.put(key, ChunkSnapshotUtil.snap(ch));
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

    public boolean isWithinWatchRadius(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int dcx = loc.getChunk().getX() - centerChunkX;
        int dcz = loc.getChunk().getZ() - centerChunkZ;
        return Math.max(Math.abs(dcx), Math.abs(dcz)) <= watchRadius;
    }

    public void restoreAll(World world) {
        for (Map.Entry<ChunkKey, ChunkSnapshot> entry : snapshots.entrySet()) {
            ChunkKey key = entry.getKey();
            if (!key.getWorld().equals(world.getName())) {
                continue;
            }
            ChunkSnapshotUtil.restore(world, key.getX(), key.getZ(), entry.getValue());
        }
    }

    public void unclaimExtraChunks(World world, FactionsBridge factions, Set<ChunkKey> claimedChunks) throws Exception {
        Set<ChunkKey> claimed = claimedChunks == null ? new HashSet<ChunkKey>() : claimedChunks;
        for (ChunkKey key : snapshots.keySet()) {
            if (!key.getWorld().equals(world.getName()) || claimed.contains(key)) {
                continue;
            }
            factions.unclaimChunk(world.getChunkAt(key.getX(), key.getZ()));
        }
    }
}
