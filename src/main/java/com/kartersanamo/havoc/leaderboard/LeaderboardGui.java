package com.kartersanamo.havoc.leaderboard;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.HavocBranding;
import com.kartersanamo.havoc.stats.PlayerStats;
import com.kartersanamo.havoc.stats.PlayerStatsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeaderboardGui {

    private static final int SIZE = 27;
    private static final int[] METRIC_SLOTS = {10, 11, 12, 19, 20};
    private static final PlayerStatsStore.LeaderboardPeriod PERIOD = PlayerStatsStore.LeaderboardPeriod.LIFETIME;

    private final Havoc plugin;

    public LeaderboardGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new LeaderboardGuiHolder(), SIZE,
                HavocBranding.formatGuiTitle("Leaderboards"));

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

    private ItemStack createMetricItem(PlayerStatsStore.LeaderboardMetric metric) {
        List<Map.Entry<UUID, PlayerStats>> rows = plugin.getPlayerStatsStore().top(metric, PERIOD, 10);
        int trackedPlayers = plugin.getPlayerStatsStore().top(metric, PERIOD, Integer.MAX_VALUE).size();

        ItemStack item = new ItemStack(metricIcon(metric), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + metric.label + " Leaderboard");
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GREEN + metric.label + " Leaderboard (#" + formatTrackedCount(trackedPlayers) + ")");
            lore.add(ChatColor.WHITE + "─────────────────");
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
                    lore.add(ChatColor.GREEN + "" + (i + 1) + ". " + name + " [" + value + "]");
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

    private static String formatTrackedCount(int count) {
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
}
