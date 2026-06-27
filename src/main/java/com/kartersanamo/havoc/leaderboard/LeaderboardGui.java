package com.kartersanamo.havoc.leaderboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.stats.PlayerStats;
import com.kartersanamo.havoc.stats.PlayerStatsStore;

public final class LeaderboardGui {

    private static final int SIZE = 27;
    private static final int[] METRIC_SLOTS = {11, 12, 13, 14, 15};
    private static final short GRAY_STAINED_GLASS_DATA = 7;
    private static final PlayerStatsStore.LeaderboardPeriod PERIOD = PlayerStatsStore.LeaderboardPeriod.LIFETIME;

    private final Havoc plugin;

    public LeaderboardGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new LeaderboardGuiHolder(), SIZE,
                "Havoc Leaderboards");

        ItemStack filler = createFillerItem();
        for (int i = 0; i < SIZE; i++) {
            if (isBorderSlot(i)) {
                inv.setItem(i, filler.clone());
            }
        }

        PlayerStatsStore.LeaderboardMetric[] metrics = PlayerStatsStore.LeaderboardMetric.values();
        for (int i = 0; i < metrics.length && i < METRIC_SLOTS.length; i++) {
            inv.setItem(METRIC_SLOTS[i], createMetricItem(metrics[i]));
        }

        player.openInventory(inv);
    }

    public boolean isLeaderboardInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder holder = inv.getHolder();
        return holder instanceof LeaderboardGuiHolder;
    }

    public void handleClick(Player player, Inventory top, int rawSlot) {
        // View-only GUI.
    }

    private static boolean isBorderSlot(int slot) {
        if (slot < 9 || slot >= 18) {
            return true;
        }
        return slot == 9 || slot == 17;
    }

    private static ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE, 1, GRAY_STAINED_GLASS_DATA);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMetricItem(PlayerStatsStore.LeaderboardMetric metric) {
        List<Map.Entry<UUID, PlayerStats>> rows = plugin.getPlayerStatsStore().top(metric, PERIOD, 10);
        int trackedPlayers = plugin.getPlayerStatsStore().top(metric, PERIOD, Integer.MAX_VALUE).size();

        ItemStack item = new ItemStack(metricIcon(metric), 1);
        ItemMeta meta = item.getItemMeta();

        ChatColor darkColor = getDarkColor(metric);
        ChatColor lightColor = getLightColor(metric);

        if (meta != null) {
            meta.setDisplayName(lightColor + metric.label + " Leaderboard");
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.WHITE + "" + ChatColor.STRIKETHROUGH + "--------------------");
            if (rows.isEmpty()) {
                lore.add(ChatColor.GREEN + "No entries yet.");
            } else {
                for (int i = 0; i < rows.size(); i++) {
                    Map.Entry<UUID, PlayerStats> row = rows.get(i);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(row.getKey());
                    String name = op != null && op.getName() != null
                            ? op.getName()
                            : row.getKey().toString().substring(0, 8);
                    int value = metricValue(row.getValue(), metric);
                    lore.add(ChatColor.GRAY + "" + (i + 1) + ". " + darkColor + name + ChatColor.GRAY + " [" + lightColor + value + ChatColor.GRAY + "]");
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material metricIcon(PlayerStatsStore.LeaderboardMetric metric) {
        switch (metric) {
            case SALVAGE_SPENT:
                return Material.GOLD_NUGGET;
            case BREACHES_PARTICIPATED:
                return Material.TNT;
            case BREACHES_TRIGGERED:
                return Material.FIREBALL;
            case SHOP_PURCHASES:
                return Material.CHEST;
            case SALVAGE_EARNED:
            default:
                return Material.GOLD_INGOT;
        }
    }

    private static int metricValue(PlayerStats stats, PlayerStatsStore.LeaderboardMetric metric) {
        switch (metric) {
            case SALVAGE_SPENT:
                return stats.salvageSpent;
            case BREACHES_PARTICIPATED:
                return stats.breachesParticipated;
            case BREACHES_TRIGGERED:
                return stats.breachesTriggered;
            case SHOP_PURCHASES:
                return stats.shopPurchases;
            case SALVAGE_EARNED:
            default:
                return stats.salvageEarned;
        }
    }

    private static ChatColor getDarkColor(PlayerStatsStore.LeaderboardMetric metric) {
        switch (metric) {
            case SALVAGE_SPENT:
                return ChatColor.DARK_RED;
            case BREACHES_PARTICIPATED:
                return ChatColor.DARK_AQUA;
            case BREACHES_TRIGGERED:
                return ChatColor.DARK_PURPLE;
            case SHOP_PURCHASES:
                return ChatColor.DARK_BLUE;
            case SALVAGE_EARNED:
                return ChatColor.DARK_GREEN;
            default:
                return ChatColor.DARK_GRAY;
        }
    }

    private static ChatColor getLightColor(PlayerStatsStore.LeaderboardMetric metric) {
        switch (metric) {
            case SALVAGE_SPENT:
                return ChatColor.RED;
            case BREACHES_PARTICIPATED:
                return ChatColor.AQUA;
            case BREACHES_TRIGGERED:
                return ChatColor.LIGHT_PURPLE;
            case SHOP_PURCHASES:
                return ChatColor.BLUE;
            case SALVAGE_EARNED:
                return ChatColor.GREEN;
            default:
                return ChatColor.GRAY;
        }
    }
}
