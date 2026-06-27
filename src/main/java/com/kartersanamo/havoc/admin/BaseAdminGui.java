package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseState;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.message.HavocBranding;
import com.kartersanamo.havoc.permission.PermissionNodes;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BaseAdminGui {

    private static final int LIST_ROWS = 6;
    private static final int LIST_SIZE = LIST_ROWS * 9;
    private static final int LIST_CAPACITY = LIST_SIZE;

    private final Havoc plugin;

    public BaseAdminGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        List<ActiveHavocBase> sortedBases = sortBases(player, plugin.getBaseService().listAllBasesSorted());
        List<ActiveHavocBase> displayedBases = sortedBases.subList(0, Math.min(LIST_CAPACITY, sortedBases.size()));

        Inventory inv = Bukkit.createInventory(new BaseAdminListHolder(displayedBases), LIST_SIZE,
                HavocBranding.formatGuiTitle("Bases"));
        for (int i = 0; i < displayedBases.size(); i++) {
            inv.setItem(i, createBaseItem(displayedBases.get(i), player));
        }
        player.openInventory(inv);
    }

    public boolean isAdminInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder h = inv.getHolder();
        return h instanceof BaseAdminListHolder;
    }

    public void handleClick(Player player, Inventory top, int rawSlot) {
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BaseAdminListHolder)) {
            return;
        }
        handleListClick(player, (BaseAdminListHolder) holder, rawSlot);
    }

    private void handleListClick(Player player, BaseAdminListHolder holder, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= holder.baseIds.size()) {
            return;
        }
        UUID id = holder.baseIds.get(rawSlot);
        ActiveHavocBase b = plugin.getBaseService().getBaseById(id);
        if (b == null) {
            plugin.getMessages().send(player, "admin.list.base-missing");
            openList(player);
            return;
        }
        if (!player.hasPermission(PermissionNodes.ADMIN_LIST_TELEPORT)) {
            plugin.getMessages().send(player, "command.no-permission");
            return;
        }
        World w = Bukkit.getWorld(b.worldName);
        if (w == null) {
            plugin.getMessages().send(player, "admin.list.world-not-loaded");
            return;
        }
        player.closeInventory();
        player.teleport(new Location(w, b.obsidianCenterX + 0.5, 256.0, b.obsidianCenterZ + 0.5));
        plugin.getMessages().send(player, "admin.list.teleport-success",
                MessageVars.one(MessageKeys.ID, shortId(b.id)));
        plugin.getLogService().log("ADMIN_BASE_TELEPORT", player.getName(), shortId(b.id),
                new Location(w, b.obsidianCenterX, b.obsidianCenterY, b.obsidianCenterZ), "teleport via GUI");
    }

    private ItemStack createBaseItem(ActiveHavocBase b, Player viewer) {
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
            double dist = distanceFrom(viewer, b);
            lore.add(ChatColor.GRAY + "Distance: " + ChatColor.WHITE + (dist < 0 ? "N/A" : ((int) Math.round(dist) + " blocks")));
            lore.add(ChatColor.DARK_GRAY + "Click to teleport");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<ActiveHavocBase> sortBases(Player viewer, List<ActiveHavocBase> bases) {
        List<ActiveHavocBase> out = new ArrayList<ActiveHavocBase>(bases);
        Collections.sort(out, new Comparator<ActiveHavocBase>() {
            @Override
            public int compare(ActiveHavocBase a, ActiveHavocBase b) {
                return compareActiveThenDifficulty(a, b);
            }
        });
        return out;
    }

    private int compareActiveThenDifficulty(ActiveHavocBase a, ActiveHavocBase b) {
        boolean aActive = a.state == BaseState.ACTIVE;
        boolean bActive = b.state == BaseState.ACTIVE;
        if (aActive != bActive) {
            return aActive ? -1 : 1;
        }
        int diffCmp = Integer.compare(a.difficulty.ordinal(), b.difficulty.ordinal());
        if (diffCmp != 0) {
            return diffCmp;
        }
        return shortId(a.id).compareTo(shortId(b.id));
    }

    private double distanceFrom(Player viewer, ActiveHavocBase b) {
        if (viewer == null) {
            return -1.0;
        }
        World world = Bukkit.getWorld(b.worldName);
        if (world == null || viewer.getWorld() != world) {
            return -1.0;
        }
        double dx = viewer.getLocation().getX() - (b.obsidianCenterX + 0.5);
        double dy = viewer.getLocation().getY() - (b.obsidianCenterY + 0.5);
        double dz = viewer.getLocation().getZ() - (b.obsidianCenterZ + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

}
