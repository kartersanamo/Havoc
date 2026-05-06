package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class PlayerMoveDenyListener implements Listener {

    private final Havoc plugin;
    private final RaidLockNotifier raidLockNotifier;

    public PlayerMoveDenyListener(Havoc plugin, RaidLockNotifier raidLockNotifier) {
        this.plugin = plugin;
        this.raidLockNotifier = raidLockNotifier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (plugin.getBaseService().shouldDenyEnter(event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            raidLockNotifier.notifyLocked(event.getPlayer(), "raid.locked.entry-denied");
        }
    }
}
