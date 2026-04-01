package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class HavocListener implements Listener {

    private final Havoc plugin;

    public HavocListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeBreach(EntityExplodeEvent event) {
        plugin.getBaseService().tryBreachFromExplosion(event.blockList(), event.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplodeLock(EntityExplodeEvent event) {
        BaseService bases = plugin.getBaseService();
        for (org.bukkit.block.Block b : event.blockList()) {
            if (bases.shouldCancelBlockChange(b.getLocation())) {
                event.blockList().clear();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BaseService bases = plugin.getBaseService();
        if (bases.shouldCancelBlockChange(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        bases.tryBreachBlock(event.getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getBaseService().shouldCancelBlockChange(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
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
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) {
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getSalvageShop().isShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getSalvageShop().handleClick((org.bukkit.entity.Player) event.getWhoClicked(), event.getRawSlot());
            }
        }
    }
}
