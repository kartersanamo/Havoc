package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.material.Dispenser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HavocListener implements Listener {

    private final Havoc plugin;
    private final ConcurrentHashMap<UUID, Long> notifyCooldownMs = new ConcurrentHashMap<UUID, Long>();
    private final ConcurrentHashMap<UUID, TntOrigin> tntOrigins = new ConcurrentHashMap<UUID, TntOrigin>();
    private final List<PendingDispense> pendingDispenses = Collections.synchronizedList(new ArrayList<PendingDispense>());

    public HavocListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeBreach(EntityExplodeEvent event) {
        Player winner = resolveExplosionWinner(event);
        plugin.getBaseService().tryBreachFromExplosion(event.blockList(), event.getLocation(), winner);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() != Material.DISPENSER) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.TNT) {
            return;
        }
        Dispenser dispenser = (Dispenser) event.getBlock().getState().getData();
        BlockFace face = dispenser.getFacing();
        Block dispenserBlock = event.getBlock();
        Location birthEstimate = dispenserBlock.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
        Object factionAtDispenser = null;
        try {
            factionAtDispenser = plugin.getFactionsBridge().getFactionAtLocation(dispenserBlock.getLocation());
            if (factionAtDispenser == null || plugin.getFactionsBridge().isWilderness(factionAtDispenser)) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        synchronized (pendingDispenses) {
            pruneExpiredPending();
            pendingDispenses.add(new PendingDispense(System.currentTimeMillis(), birthEstimate, factionAtDispenser));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) {
            return;
        }
        PendingDispense match = null;
        synchronized (pendingDispenses) {
            pruneExpiredPending();
            double best = 4.0D; // squared distance threshold
            for (int i = 0; i < pendingDispenses.size(); i++) {
                PendingDispense p = pendingDispenses.get(i);
                if (p.birthEstimate.getWorld() == null || event.getLocation().getWorld() == null) {
                    continue;
                }
                if (!p.birthEstimate.getWorld().equals(event.getLocation().getWorld())) {
                    continue;
                }
                double d = p.birthEstimate.distanceSquared(event.getLocation());
                if (d <= best) {
                    best = d;
                    match = p;
                }
            }
            if (match != null) {
                pendingDispenses.remove(match);
            }
        }
        if (match != null) {
            tntOrigins.put(event.getEntity().getUniqueId(), new TntOrigin(match.birthEstimate, match.factionAtDispenser));
        }
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
        if (event.getView().getTopInventory() != null && plugin.getBaseAdminGui().isAdminInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getBaseAdminGui().handleClick((org.bukkit.entity.Player) event.getWhoClicked(),
                        event.getView().getTopInventory(), event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getSalvageShop().isShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getSalvageShop().handleClick((org.bukkit.entity.Player) event.getWhoClicked(), event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getLeaderboardGui().isLeaderboardInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getLeaderboardGui().handleClick((org.bukkit.entity.Player) event.getWhoClicked(),
                        event.getView().getTopInventory(), event.getRawSlot());
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

    private Player resolveExplosionWinner(EntityExplodeEvent event) {
        if (event.getEntity() == null) {
            return null;
        }
        TntOrigin origin = tntOrigins.remove(event.getEntity().getUniqueId());
        if (origin == null || origin.birthLocation == null || origin.birthLocation.getWorld() == null || origin.factionAtDispenser == null) {
            return null;
        }
        World world = origin.birthLocation.getWorld();
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            Object pf;
            try {
                pf = plugin.getFactionsBridge().getPlayerFaction(p);
                if (pf == null || !plugin.getFactionsBridge().factionsEqual(pf, origin.factionAtDispenser)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            double d = p.getLocation().distanceSquared(origin.birthLocation);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void pruneExpiredPending() {
        long cutoff = System.currentTimeMillis() - 5000L;
        Iterator<PendingDispense> it = pendingDispenses.iterator();
        while (it.hasNext()) {
            PendingDispense p = it.next();
            if (p.createdMs < cutoff) {
                it.remove();
            }
        }
    }

    private static final class PendingDispense {
        final long createdMs;
        final Location birthEstimate;
        final Object factionAtDispenser;

        PendingDispense(long createdMs, Location birthEstimate, Object factionAtDispenser) {
            this.createdMs = createdMs;
            this.birthEstimate = birthEstimate;
            this.factionAtDispenser = factionAtDispenser;
        }
    }

    private static final class TntOrigin {
        final Location birthLocation;
        final Object factionAtDispenser;

        TntOrigin(Location birthLocation, Object factionAtDispenser) {
            this.birthLocation = birthLocation;
            this.factionAtDispenser = factionAtDispenser;
        }
    }
}
