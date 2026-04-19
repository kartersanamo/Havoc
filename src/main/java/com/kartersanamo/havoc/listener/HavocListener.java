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

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HavocListener implements Listener {

    private final Havoc plugin;
    private final ConcurrentHashMap<UUID, Long> notifyCooldownMs = new ConcurrentHashMap<UUID, Long>();

    public HavocListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeBreach(EntityExplodeEvent event) {
        plugin.getBaseService().tryBreachFromExplosion(event.blockList(), event.getLocation());
    }

    /**
     * Strip only Havoc-locked blocks from the explosion list. Clearing the entire list (old behavior) removed
     * every block—including breach obsidian—when any one block hit satellite/restore protection.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplodeLock(EntityExplodeEvent event) {
        BaseService bases = plugin.getBaseService();
        List<org.bukkit.block.Block> blocks = event.blockList();
        int removed = 0;
        for (Iterator<org.bukkit.block.Block> it = blocks.iterator(); it.hasNext(); ) {
            org.bukkit.block.Block b = it.next();
            if (bases.shouldCancelBlockChange(b.getLocation())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().fine("[Havoc] Explosion: removed " + removed + " block(s) from break list (restore / satellite lock).");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BaseService bases = plugin.getBaseService();
        if (bases.shouldCancelBlockChange(event.getBlock().getLocation())) {
            event.setCancelled(true);
            notifyLocked(event.getPlayer(), "raid.locked.block-change");
            return;
        }
        bases.tryBreachBlock(event.getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getBaseService().shouldCancelBlockChange(event.getBlock().getLocation())) {
            event.setCancelled(true);
            notifyLocked(event.getPlayer(), "raid.locked.block-change");
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
            notifyLocked(event.getPlayer(), "raid.locked.entry-denied");
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

    private void notifyLocked(org.bukkit.entity.Player player, String key) {
        long now = System.currentTimeMillis();
        Long prev = notifyCooldownMs.get(player.getUniqueId());
        if (prev != null && now - prev < plugin.getHavocConfig().getLockNotifyCooldownMs()) {
            return;
        }
        notifyCooldownMs.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, key);
    }
}
