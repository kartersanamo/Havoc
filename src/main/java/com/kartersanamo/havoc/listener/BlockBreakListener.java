package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class BlockBreakListener implements Listener {

    private final Havoc plugin;
    private final RaidLockNotifier raidLockNotifier;

    public BlockBreakListener(Havoc plugin, RaidLockNotifier raidLockNotifier) {
        this.plugin = plugin;
        this.raidLockNotifier = raidLockNotifier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakCancel(BlockBreakEvent event) {
        BaseService bases = plugin.getBaseService();
        if (bases.shouldCancelBlockChange(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            raidLockNotifier.notifyLocked(event.getPlayer(), "raid.locked.block-change");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakBreach(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.OBSIDIAN) {
            return;
        }
        final Location location = event.getBlock().getLocation().clone();
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getBaseService().tryBreachBrokenInnerObsidian(location, player);
            }
        }.runTask(plugin);
    }
}
