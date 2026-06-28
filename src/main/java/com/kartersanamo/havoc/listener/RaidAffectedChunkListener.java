package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Snapshots raid-adjacent chunks the first time they are modified so cleanup can revert cannons
 * and terrain outside the pre-spawn watch ring.
 */
public final class RaidAffectedChunkListener implements Listener {

    private final Havoc plugin;

    public RaidAffectedChunkListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        plugin.getBaseService().trackAffectedChunk(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        plugin.getBaseService().trackAffectedChunk(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getLocation() == null) {
            return;
        }
        plugin.getBaseService().trackAffectedChunk(event.getLocation());
        for (org.bukkit.block.Block block : event.blockList()) {
            if (block != null) {
                plugin.getBaseService().trackAffectedChunk(block.getLocation());
            }
        }
    }
}
