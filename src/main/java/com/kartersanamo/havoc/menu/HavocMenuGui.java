package com.kartersanamo.havoc.menu;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.base.BaseState;
import com.kartersanamo.havoc.config.HavocConfig;
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
import java.util.List;

public final class HavocMenuGui {

    private static final int SIZE = 9;
    private static final int WARP_SLOT = 4;

    private final Havoc plugin;

    public HavocMenuGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new HavocMenuGuiHolder(), SIZE, menuTitle());
        inv.setItem(WARP_SLOT, createWarpItem());
        player.openInventory(inv);
    }

    public boolean isMenuInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder holder = inv.getHolder();
        return holder instanceof HavocMenuGuiHolder;
    }

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot != WARP_SLOT) {
            return;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        Location spawn = cfg.getSpawnLocation();
        World world = spawn.getWorld();
        if (world == null) {
            plugin.getMessages().send(player, "menu.warp-failed");
            player.closeInventory();
            return;
        }
        player.closeInventory();
        player.teleport(spawn);
        plugin.getMessages().send(player, "menu.warp-success");
    }

    private ItemStack createWarpItem() {
        BaseSnapshot snapshot = snapshotBases();
        HavocConfig cfg = plugin.getHavocConfig();
        int borderSize = cfg.getBorderHalfSize() * 2;

        ItemStack item = new ItemStack(Material.TNT, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Havoc");
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Havoc (#" + formatCount(snapshot.activeTotal) + ")");
            lore.add(" ");
            lore.add(ChatColor.WHITE + "Raid procedurally spawned faction bases across the "
                    + ChatColor.GRAY + cfg.getWorldName() + ChatColor.WHITE + ". Breach obsidian walls with TNT, "
                    + "earn Salvage, and climb the leaderboards.");
            lore.add(" ");
            lore.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Information:");
            lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + borderSize + "x" + borderSize + " world border");
            lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + snapshot.activeTotal + " active base"
                    + (snapshot.activeTotal == 1 ? "" : "s")
                    + ChatColor.GRAY + " (Easy " + snapshot.easy + ", Medium " + snapshot.medium + ", Hard " + snapshot.hard + ")");
            if (snapshot.restoring > 0) {
                lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + snapshot.restoring + " base"
                        + (snapshot.restoring == 1 ? "" : "s") + ChatColor.GRAY + " currently restoring");
            }
            lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + "Salvage shop, stats, and leaderboards via /havoc");
            lore.add(" ");
            lore.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Status:");
            lore.add(ChatColor.GRAY + "- " + ChatColor.GREEN + "" + ChatColor.BOLD + "OPEN "
                    + ChatColor.GRAY + "(Click to warp!)");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private BaseSnapshot snapshotBases() {
        int easy = 0;
        int medium = 0;
        int hard = 0;
        int restoring = 0;
        for (ActiveHavocBase base : plugin.getBaseService().listAllBasesSorted()) {
            if (base.state == BaseState.RESTORING) {
                restoring++;
                continue;
            }
            if (base.state != BaseState.ACTIVE) {
                continue;
            }
            if (base.difficulty == BaseDifficulty.EASY) {
                easy++;
            } else if (base.difficulty == BaseDifficulty.MEDIUM) {
                medium++;
            } else if (base.difficulty == BaseDifficulty.HARD) {
                hard++;
            }
        }
        return new BaseSnapshot(easy + medium + hard, easy, medium, hard, restoring);
    }

    private static String menuTitle() {
        return ChatColor.DARK_RED + "" + ChatColor.BOLD + "Havoc";
    }

    private static String formatCount(int count) {
        String raw = String.valueOf(Math.max(0, count));
        if (raw.length() >= 4) {
            return raw;
        }
        StringBuilder padded = new StringBuilder();
        for (int i = raw.length(); i < 4; i++) {
            padded.append('0');
        }
        padded.append(raw);
        return padded.toString();
    }

    private static final class BaseSnapshot {
        final int activeTotal;
        final int easy;
        final int medium;
        final int hard;
        final int restoring;

        BaseSnapshot(int activeTotal, int easy, int medium, int hard, int restoring) {
            this.activeTotal = activeTotal;
            this.easy = easy;
            this.medium = medium;
            this.hard = hard;
            this.restoring = restoring;
        }
    }
}
