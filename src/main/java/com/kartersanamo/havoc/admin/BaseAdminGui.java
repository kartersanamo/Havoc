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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BaseAdminGui {

    private static final int LIST_ROWS = 6;
    private static final int LIST_SIZE = LIST_ROWS * 9;
    private static final int LIST_PAGE_SIZE = 45;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_REFRESH = 49;
    private static final int SLOT_SORT = 51;
    private static final int SLOT_NEXT_PAGE = 53;

    private final Havoc plugin;

    public BaseAdminGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        openList(player, 0, BaseAdminGuiSortMode.ACTIVE_FIRST);
    }

    private void openList(Player player, int requestedPage, BaseAdminGuiSortMode sortMode) {
        List<ActiveHavocBase> sortedBases = sortBases(player, plugin.getBaseService().listAllBasesSorted(), sortMode);
        int totalPages = pageCount(sortedBases.size());
        int page = clampPage(requestedPage, totalPages);

        Inventory inv = Bukkit.createInventory(new BaseAdminListHolder(sortedBases, page, sortMode), LIST_SIZE,
                ChatColor.DARK_AQUA + "Havoc Bases");
        int start = page * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, sortedBases.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            inv.setItem(slot++, createBaseItem(sortedBases.get(i), player));
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, item(Material.ARROW, ChatColor.AQUA + "Previous Page",
                    Collections.singletonList(ChatColor.GRAY + "Go to page " + page + ".")));
        }
        inv.setItem(SLOT_REFRESH, item(Material.COMPASS, ChatColor.AQUA + "Refresh",
                Collections.singletonList(ChatColor.GRAY + "Reload bases on this page.")));
        inv.setItem(SLOT_SORT, item(Material.HOPPER, ChatColor.GOLD + "Sort: " + sortMode.label,
                Collections.singletonList(ChatColor.GRAY + "Click to cycle sort mode.")));
        if (page + 1 < totalPages) {
            inv.setItem(SLOT_NEXT_PAGE, item(Material.ARROW, ChatColor.AQUA + "Next Page",
                    Collections.singletonList(ChatColor.GRAY + "Go to page " + (page + 2) + ".")));
        }

        inv.setItem(47, item(Material.PAPER, ChatColor.YELLOW + "Page " + (page + 1) + "/" + totalPages,
                Collections.singletonList(ChatColor.GRAY.toString() + sortedBases.size() + " total bases")));
        player.openInventory(inv);
    }

    public boolean isAdminInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder h = inv.getHolder();
        return h instanceof BaseAdminListHolder || h instanceof BaseAdminDetailHolder;
    }

    public void handleClick(Player player, Inventory top, int rawSlot) {
        InventoryHolder holder = top.getHolder();
        if (holder instanceof BaseAdminListHolder) {
            handleListClick(player, (BaseAdminListHolder) holder, rawSlot);
            return;
        }
        if (holder instanceof BaseAdminDetailHolder) {
            handleDetailClick(player, (BaseAdminDetailHolder) holder, rawSlot);
        }
    }

    private void handleListClick(Player player, BaseAdminListHolder holder, int rawSlot) {
        if (rawSlot == SLOT_PREV_PAGE) {
            openList(player, holder.page - 1, holder.sortMode);
            return;
        }
        if (rawSlot == SLOT_NEXT_PAGE) {
            openList(player, holder.page + 1, holder.sortMode);
            return;
        }
        if (rawSlot == SLOT_REFRESH) {
            openList(player, holder.page, holder.sortMode);
            return;
        }
        if (rawSlot == SLOT_SORT) {
            openList(player, 0, holder.sortMode.next());
            return;
        }
        if (rawSlot < 0 || rawSlot >= LIST_PAGE_SIZE) {
            return;
        }
        int index = holder.page * LIST_PAGE_SIZE + rawSlot;
        if (index < 0 || index >= holder.baseIds.size()) {
            return;
        }
        UUID id = holder.baseIds.get(index);
        ActiveHavocBase b = plugin.getBaseService().getBaseById(id);
        if (b == null) {
            player.sendMessage(ChatColor.RED + "That base no longer exists.");
            openList(player, holder.page, holder.sortMode);
            return;
        }
        openDetail(player, b, holder.page, holder.sortMode);
    }

    private void openDetail(Player player, ActiveHavocBase b, int returnPage, BaseAdminGuiSortMode returnSortMode) {
        Inventory inv = Bukkit.createInventory(new BaseAdminDetailHolder(b.id, returnPage, returnSortMode), 27,
                ChatColor.DARK_BLUE + "Base " + shortId(b.id));
        inv.setItem(11, createBaseItem(b));
        inv.setItem(15, createControlItem(Material.ENDER_PEARL, ChatColor.GREEN + "Teleport To Base",
                ChatColor.GRAY + "Teleport above obsidian center."));
        inv.setItem(16, createControlItem(Material.REDSTONE, ChatColor.RED + "Force Restore",
                ChatColor.GRAY + "Immediately start restore phase."));
        inv.setItem(22, createControlItem(Material.ARROW, ChatColor.AQUA + "Back To List",
                ChatColor.GRAY + "Return to base list."));
        player.openInventory(inv);
    }

    private void handleDetailClick(Player player, BaseAdminDetailHolder holder, int rawSlot) {
        ActiveHavocBase b = plugin.getBaseService().getBaseById(holder.baseId);
        if (b == null) {
            player.sendMessage(ChatColor.RED + "That base no longer exists.");
            openList(player, holder.returnPage, holder.returnSortMode);
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
            plugin.getLogService().log("ADMIN_BASE_TELEPORT", player.getName(), shortId(b.id),
                    new Location(w, b.obsidianCenterX, b.obsidianCenterY, b.obsidianCenterZ), "teleport via GUI");
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
            plugin.getLogService().log("ADMIN_BASE_FORCE_RESTORE", player.getName(), shortId(b.id),
                    new Location(Bukkit.getWorld(b.worldName), b.obsidianCenterX, b.obsidianCenterY, b.obsidianCenterZ),
                    "ok=" + ok);
            openList(player, holder.returnPage, holder.returnSortMode);
            return;
        }
        if (rawSlot == 22) {
            openList(player, holder.returnPage, holder.returnSortMode);
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
            lore.add(ChatColor.DARK_GRAY + "Click for controls");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<ActiveHavocBase> sortBases(Player viewer, List<ActiveHavocBase> bases, BaseAdminGuiSortMode mode) {
        List<ActiveHavocBase> out = new ArrayList<ActiveHavocBase>(bases);
        final Player sortViewer = viewer;
        switch (mode) {
            case DISTANCE:
                Collections.sort(out, new Comparator<ActiveHavocBase>() {
                    @Override
                    public int compare(ActiveHavocBase a, ActiveHavocBase b) {
                        int cmp = Double.compare(distanceScore(sortViewer, a), distanceScore(sortViewer, b));
                        if (cmp != 0) {
                            return cmp;
                        }
                        return compareActiveThenDifficulty(a, b);
                    }
                });
                break;
            case DIFFICULTY:
                Collections.sort(out, new Comparator<ActiveHavocBase>() {
                    @Override
                    public int compare(ActiveHavocBase a, ActiveHavocBase b) {
                        int cmp = Integer.compare(a.difficulty.ordinal(), b.difficulty.ordinal());
                        if (cmp != 0) {
                            return cmp;
                        }
                        return compareActiveThenDifficulty(a, b);
                    }
                });
                break;
            case ACTIVE_FIRST:
            default:
                Collections.sort(out, new Comparator<ActiveHavocBase>() {
                    @Override
                    public int compare(ActiveHavocBase a, ActiveHavocBase b) {
                        return compareActiveThenDifficulty(a, b);
                    }
                });
                break;
        }
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

    private double distanceScore(Player viewer, ActiveHavocBase b) {
        double d = distanceFrom(viewer, b);
        return d < 0 ? Double.MAX_VALUE : d;
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

    private int pageCount(int size) {
        int pages = (int) Math.ceil(size / (double) LIST_PAGE_SIZE);
        return Math.max(1, pages);
    }

    private int clampPage(int requestedPage, int totalPages) {
        if (requestedPage < 0) {
            return 0;
        }
        if (requestedPage >= totalPages) {
            return totalPages - 1;
        }
        return requestedPage;
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

}
