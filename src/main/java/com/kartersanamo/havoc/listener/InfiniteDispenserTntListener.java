package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.raid.DispenserVirtualTnt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InfiniteDispenserTntListener implements Listener {

    private final Havoc plugin;
    private final TntAttributionTracker tntAttributionTracker;
    private final Map<String, Boolean> poweredState = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, Long> lastSpawnMs = new ConcurrentHashMap<String, Long>();

    public InfiniteDispenserTntListener(Havoc plugin, TntAttributionTracker tntAttributionTracker) {
        this.plugin = plugin;
        this.tntAttributionTracker = tntAttributionTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER || !isRaidCannonDispenser(block.getLocation())) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.TNT) {
            return;
        }
        event.setCancelled(true);
        tryVirtualDispense(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenserPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER || !isRaidCannonDispenser(block.getLocation())) {
            return;
        }
        boolean powered = block.getBlockPower() > 0 || block.isBlockIndirectlyPowered();
        String key = blockKey(block);
        Boolean wasPowered = poweredState.put(key, powered);
        if (!powered || Boolean.TRUE.equals(wasPowered)) {
            return;
        }
        Dispenser state = (Dispenser) block.getState();
        if (!DispenserVirtualTnt.isTntOnlyOrEmpty(state)) {
            return;
        }
        tryVirtualDispense(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupTnt(PlayerPickupItemEvent event) {
        Item entity = event.getItem();
        if (entity == null || entity.getItemStack().getType() != Material.TNT) {
            return;
        }
        if (!isRaidCannonDispenser(entity.getLocation())) {
            return;
        }
        event.setCancelled(true);
        entity.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoveTntFromDispenser(InventoryMoveItemEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null || stack.getType() != Material.TNT) {
            return;
        }
        Inventory source = event.getSource();
        if (source == null || source.getType() != InventoryType.DISPENSER) {
            return;
        }
        Location loc = dispenserLocation(source);
        if (loc == null || !isRaidCannonDispenser(loc)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeTntFromDispenser(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (!isTnt(current) && !isTnt(cursor)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() != InventoryType.DISPENSER) {
            return;
        }
        Location loc = dispenserLocation(top);
        if (loc == null || !isRaidCannonDispenser(loc)) {
            return;
        }
        if (event.getRawSlot() < top.getSize()) {
            event.setCancelled(true);
        }
    }

    private boolean isRaidCannonDispenser(Location location) {
        if (!plugin.getHavocConfig().isHavocWorld(location)) {
            return false;
        }
        return plugin.getBaseService().resolveRaidCannonFaction(location) != null;
    }

    private void tryVirtualDispense(Block dispenserBlock) {
        if (debounce(dispenserBlock)) {
            return;
        }
        Dispenser state = (Dispenser) dispenserBlock.getState();
        if (!DispenserVirtualTnt.isTntOnlyOrEmpty(state)) {
            return;
        }
        tntAttributionTracker.registerDispenserActivation(dispenserBlock);
        DispenserVirtualTnt.spawnPrimedTnt(dispenserBlock);
    }

    private boolean debounce(Block block) {
        String key = blockKey(block);
        long now = System.currentTimeMillis();
        Long last = lastSpawnMs.put(key, now);
        return last != null && now - last.longValue() < 100L;
    }

    private static boolean isTnt(ItemStack stack) {
        return stack != null && stack.getType() == Material.TNT;
    }

    private static Location dispenserLocation(Inventory inventory) {
        if (!(inventory.getHolder() instanceof org.bukkit.block.BlockState)) {
            return null;
        }
        org.bukkit.block.BlockState holder = (org.bukkit.block.BlockState) inventory.getHolder();
        return holder.getLocation();
    }

    private static String blockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
