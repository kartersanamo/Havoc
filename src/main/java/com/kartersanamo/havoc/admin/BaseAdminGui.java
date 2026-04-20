package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BaseAdminGui {

    private final Havoc plugin;

    public BaseAdminGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        List<ActiveHavocBase> bases = plugin.getBaseService().listAllBasesSorted();
        int rows = 6;
        Inventory inv = Bukkit.createInventory(new ListHolder(bases), rows * 9, ChatColor.DARK_AQUA + "Havoc Bases");
        int slot = 0;
        for (ActiveHavocBase b : bases) {
            if (slot >= 45) {
                break;
            }
            inv.setItem(slot++, createBaseItem(b));
        }
        inv.setItem(49, item(Material.COMPASS, ChatColor.AQUA + "Refresh", Collections.singletonList(ChatColor.GRAY + "Click to refresh this list.")));
        player.openInventory(inv);
    }

    public boolean isAdminInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder h = inv.getHolder();
        return h instanceof ListHolder || h instanceof DetailHolder;
    }

    public void handleClick(Player player, Inventory top, int rawSlot) {
        InventoryHolder holder = top.getHolder();
        if (holder instanceof ListHolder) {
            handleListClick(player, (ListHolder) holder, rawSlot);
            return;
        }
        if (holder instanceof DetailHolder) {
            handleDetailClick(player, (DetailHolder) holder, rawSlot);
        }
    }

    private void handleListClick(Player player, ListHolder holder, int rawSlot) {
        if (rawSlot == 49) {
            openList(player);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 45 || rawSlot >= holder.baseIds.size()) {
            return;
        }
        UUID id = holder.baseIds.get(rawSlot);
        ActiveHavocBase b = plugin.getBaseService().getBaseById(id);
        if (b == null) {
            player.sendMessage(ChatColor.RED + "That base no longer exists.");
            openList(player);
            return;
        }
        openDetail(player, b);
    }

    private void openDetail(Player player, ActiveHavocBase b) {
        Inventory inv = Bukkit.createInventory(new DetailHolder(b.id), 27, ChatColor.DARK_BLUE + "Base " + shortId(b.id));
        inv.setItem(11, createBaseItem(b));
        inv.setItem(15, createControlItem(Material.ENDER_PEARL, ChatColor.GREEN + "Teleport To Base",
                ChatColor.GRAY + "Teleport above obsidian center."));
        inv.setItem(16, createControlItem(Material.REDSTONE, ChatColor.RED + "Force Restore",
                ChatColor.GRAY + "Immediately start restore phase."));
        inv.setItem(22, createControlItem(Material.ARROW, ChatColor.AQUA + "Back To List",
                ChatColor.GRAY + "Return to base list."));
        player.openInventory(inv);
    }

    private void handleDetailClick(Player player, DetailHolder holder, int rawSlot) {
        ActiveHavocBase b = plugin.getBaseService().getBaseById(holder.baseId);
        if (b == null) {
            player.sendMessage(ChatColor.RED + "That base no longer exists.");
            openList(player);
            return;
        }
        if (rawSlot == 15) {
            World w = Bukkit.getWorld(b.worldName);
            if (w == null) {
                player.sendMessage(ChatColor.RED + "Base world is not loaded.");
                return;
            }
            player.teleport(new Location(w, b.obsidianCenterX + 0.5, b.obsidianCenterY + 2.0, b.obsidianCenterZ + 0.5));
            player.sendMessage(ChatColor.GREEN + "Teleported to base " + shortId(b.id) + ".");
            return;
        }
        if (rawSlot == 16) {
            if (b.state != BaseState.ACTIVE) {
                player.sendMessage(ChatColor.RED + "Base is already restoring.");
                return;
            }
            boolean ok = plugin.getBaseService().adminForceStartRestore(b.id);
            player.sendMessage(ok ? ChatColor.GREEN + "Forced restore for base " + shortId(b.id) + "."
                    : ChatColor.RED + "Could not force restore that base.");
            openList(player);
            return;
        }
        if (rawSlot == 22) {
            openList(player);
        }
    }

    private ItemStack createBaseItem(ActiveHavocBase b) {
        Material mat = b.state == BaseState.ACTIVE ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + b.difficulty.name() + ChatColor.GRAY + " [" + shortId(b.id) + "]");
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "State: " + (b.state == BaseState.ACTIVE ? ChatColor.GREEN : ChatColor.RED) + b.state.name());
            lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + b.worldName);
            lore.add(ChatColor.GRAY + "Coords: " + ChatColor.YELLOW + b.obsidianCenterX + ", " + b.obsidianCenterY + ", " + b.obsidianCenterZ);
            lore.add(ChatColor.GRAY + "Claims: " + ChatColor.WHITE + b.claimedChunks.size());
            lore.add(ChatColor.GRAY + "Players Nearby: " + ChatColor.WHITE + nearbyPlayers(b));
            lore.add(ChatColor.DARK_GRAY + "Click for controls");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int nearbyPlayers(ActiveHavocBase b) {
        World w = Bukkit.getWorld(b.worldName);
        if (w == null) {
            return 0;
        }
        int n = 0;
        double r = plugin.getHavocConfig().getRewardRadius();
        double rsq = r * r;
        Location center = new Location(w, b.obsidianCenterX + 0.5, b.obsidianCenterY + 0.5, b.obsidianCenterZ + 0.5);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= rsq) {
                n++;
            }
        }
        return n;
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createControlItem(Material mat, String name, String lore) {
        return item(mat, name, Collections.singletonList(lore));
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static final class ListHolder implements InventoryHolder {
        private final List<UUID> baseIds = new ArrayList<UUID>();

        private ListHolder(List<ActiveHavocBase> bases) {
            for (ActiveHavocBase b : bases) {
                baseIds.add(b.id);
            }
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class DetailHolder implements InventoryHolder {
        private final UUID baseId;

        private DetailHolder(UUID baseId) {
            this.baseId = baseId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
