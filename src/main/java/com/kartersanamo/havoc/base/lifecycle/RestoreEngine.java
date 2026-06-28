package com.kartersanamo.havoc.base.lifecycle;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseState;
import com.kartersanamo.havoc.base.ChunkKey;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.event.BaseRestoredEvent;
import com.kartersanamo.havoc.event.InternalEventBus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RestoreEngine {

    public void tick(Havoc plugin, Map<UUID, ActiveHavocBase> basesById, Map<ChunkKey, UUID> chunkOwners, InternalEventBus eventBus) {
        for (ActiveHavocBase b : new ArrayList<ActiveHavocBase>(basesById.values())) {
            if (b.state != BaseState.RESTORING || b.terrainSnapshot == null) {
                continue;
            }
            World w = Bukkit.getWorld(b.worldName);
            if (w == null) {
                continue;
            }
            int vol = b.terrainSnapshot.volume();
            int ticks = Math.max(1, plugin.getHavocConfig().getRestoreSeconds() * 20);
            int perTick = (vol + ticks - 1) / ticks;
            b.terrainSnapshot.applyPartial(w, b.restoreCursor, perTick);
            b.restoreCursor += perTick;
            if (b.restoreCursor >= vol) {
                HavocDebug.announce(plugin, "Terrain restore DONE for ~" + shortId(b.id) + " — restoring affected chunks and unclaiming "
                        + b.claimedChunks.size() + " chunk(s).");
                int claimsCount = b.claimedChunks.size();
                eventBus.publish(new BaseRestoredEvent(b,
                        new Location(w, b.obsidianCenterX, b.obsidianCenterY, b.obsidianCenterZ),
                        claimsCount));
                finishRestore(plugin, b, w);
                unregisterChunks(b, chunkOwners);
                basesById.remove(b.id);
            }
        }
    }

    private void finishRestore(Havoc plugin, ActiveHavocBase b, World w) {
        if (b.satellite != null) {
            try {
                b.satellite.restoreAll(w);
                Set<ChunkKey> claimed = new HashSet<ChunkKey>(b.claimedChunks);
                b.satellite.unclaimExtraChunks(w, plugin.getFactionsBridge(), claimed);
            } catch (Exception e) {
                plugin.getLogger().warning("Affected chunk restore failed: " + e.getMessage());
            }
        }
        try {
            for (ChunkKey key : b.claimedChunks) {
                if (!key.getWorld().equals(w.getName())) {
                    continue;
                }
                plugin.getFactionsBridge().unclaimChunk(w.getChunkAt(key.getX(), key.getZ()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Unclaim after restore: " + e.getMessage());
        }
    }

    private void unregisterChunks(ActiveHavocBase b, Map<ChunkKey, UUID> chunkOwners) {
        for (ChunkKey key : b.claimedChunks) {
            chunkOwners.remove(key);
        }
        b.claimedChunks.clear();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
