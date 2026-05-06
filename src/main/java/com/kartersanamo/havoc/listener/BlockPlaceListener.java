package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockPlaceListener implements Listener {

    private final Havoc plugin;
    private final RaidLockNotifier raidLockNotifier;

    public BlockPlaceListener(Havoc plugin, RaidLockNotifier raidLockNotifier) {
        this.plugin = plugin;
        this.raidLockNotifier = raidLockNotifier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getBaseService().shouldCancelBlockChange(event.getBlock().getLocation())) {
            event.setCancelled(true);
            raidLockNotifier.notifyLocked(event.getPlayer(), "raid.locked.block-change");
        }
    }
}
