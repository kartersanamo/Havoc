package com.kartersanamo.havoc.leaderboard;

import com.kartersanamo.havoc.Havoc;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeaderboardGui {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_PREV_METRIC = 47;
    private static final int SLOT_REFRESH = 49;
    private static final int SLOT_NEXT_METRIC = 51;
    private static final int SLOT_PERIOD = 50;

    private final Havoc plugin;

    public LeaderboardGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, PlayerStatsStore.LeaderboardMetric.SALVAGE_EARNED, PlayerStatsStore.LeaderboardPeriod.LIFETIME, 0);
    }

    public void open(Player player, PlayerStatsStore.LeaderboardMetric metric, PlayerStatsStore.LeaderboardPeriod period, int requestedPage) {
        List<Map.Entry<UUID, PlayerStats>> allRows = plugin.getPlayerStatsStore().top(metric, period, Integer.MAX_VALUE);
        int pages = Math.max(1, (allRows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Inventory inv = Bukkit.createInventory(new LeaderboardGuiHolder(metric, period, page), SIZE,
                ChatColor.DARK_PURPLE + "Havoc Leaderboards");
        decorateBackground(inv);

        int start = page * PAGE_SIZE;
        int end = Math.min(allRows.size(), start + PAGE_SIZE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            int rank = i + 1;
            inv.setItem(slot, createEntryItem(rank, allRows.get(i), metric));
            slot++;
            if (slot % 9 == 0) {
                slot += 0;
            }
        }

        inv.setItem(46, createMetricItem(metric));
        inv.setItem(48, createSummaryItem(period, allRows.size(), page + 1, pages));
        inv.setItem(SLOT_PREV_METRIC, control(Material.HOPPER, ChatColor.GOLD + "Previous Metric",
                ChatColor.GRAY + metricBefore(metric).label));
        inv.setItem(SLOT_REFRESH, control(Material.COMPASS, ChatColor.AQUA + "Refresh",
                ChatColor.GRAY + "Reload current leaderboard"));
        inv.setItem(SLOT_NEXT_METRIC, control(Material.HOPPER, ChatColor.GOLD + "Next Metric",
                ChatColor.GRAY + metricAfter(metric).label));
        inv.setItem(SLOT_PERIOD, control(Material.WATCH, ChatColor.LIGHT_PURPLE + "Period: " + period.label,
                ChatColor.GRAY + "Click to cycle period"));
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, control(Material.ARROW, ChatColor.AQUA + "Previous Page",
                    ChatColor.GRAY + "Page " + page));
        }
        if (page + 1 < pages) {
            inv.setItem(SLOT_NEXT_PAGE, control(Material.ARROW, ChatColor.AQUA + "Next Page",
                    ChatColor.GRAY + "Page " + (page + 2)));
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
        if (!(top.getHolder() instanceof LeaderboardGuiHolder)) {
            return;
        }
        LeaderboardGuiHolder holder = (LeaderboardGuiHolder) top.getHolder();
        if (rawSlot == SLOT_PREV_PAGE) {
            open(player, holder.metric, holder.period, holder.page - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT_PAGE) {
            open(player, holder.metric, holder.period, holder.page + 1);
            return;
        }
        if (rawSlot == SLOT_PREV_METRIC) {
            open(player, metricBefore(holder.metric), holder.period, 0);
            return;
        }
        if (rawSlot == SLOT_NEXT_METRIC) {
            open(player, metricAfter(holder.metric), holder.period, 0);
            return;
        }
        if (rawSlot == SLOT_PERIOD) {
            open(player, holder.metric, periodAfter(holder.period), 0);
            return;
        }
        if (rawSlot == SLOT_REFRESH) {
            open(player, holder.metric, holder.period, holder.page);
        }
    }

    private void decorateBackground(Inventory inv) {
        ItemStack border = pane((short) 7, " ");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(45, pane((short) 10, " "));
        inv.setItem(53, pane((short) 10, " "));
    }

    private ItemStack createEntryItem(int rank, Map.Entry<UUID, PlayerStats> row, PlayerStatsStore.LeaderboardMetric metric) {
        Material material = rank <= 3 ? Material.GOLD_INGOT : Material.PAPER;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(row.getKey());
            String name = op != null && op.getName() != null ? op.getName() : row.getKey().toString().substring(0, 8);
            PlayerStats stats = row.getValue();
            int value = metricValue(stats, metric);
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "#" + rank + " " + ChatColor.WHITE + name);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Metric: " + ChatColor.GOLD + metric.label);
            lore.add(ChatColor.GRAY + "Value: " + ChatColor.AQUA + value);
            lore.add(ChatColor.DARK_GRAY + " ");
            lore.add(ChatColor.GRAY + "Breaches Participated: " + ChatColor.WHITE + stats.breachesParticipated);
            lore.add(ChatColor.GRAY + "Breaches Triggered: " + ChatColor.WHITE + stats.breachesTriggered);
            lore.add(ChatColor.GRAY + "Salvage Earned: " + ChatColor.WHITE + stats.salvageEarned);
            lore.add(ChatColor.GRAY + "Salvage Spent: " + ChatColor.WHITE + stats.salvageSpent);
            lore.add(ChatColor.GRAY + "Shop Purchases: " + ChatColor.WHITE + stats.shopPurchases);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMetricItem(PlayerStatsStore.LeaderboardMetric metric) {
        return control(Material.NETHER_STAR, ChatColor.GOLD + "Current Metric", ChatColor.AQUA + metric.label);
    }

    private ItemStack createSummaryItem(PlayerStatsStore.LeaderboardPeriod period, int totalPlayers, int page, int pages) {
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Tracked players: " + ChatColor.WHITE + totalPlayers);
        lore.add(ChatColor.GRAY + "Page: " + ChatColor.WHITE + page + "/" + pages);
        long ms = plugin.getPlayerStatsStore().millisUntilNextRollover(period);
        if (ms >= 0L) {
            lore.add(ChatColor.GRAY + "Resets in: " + ChatColor.LIGHT_PURPLE + formatDuration(ms));
        } else {
            lore.add(ChatColor.GRAY + "Resets in: " + ChatColor.WHITE + "Never (lifetime)");
        }
        return control(Material.BOOK, ChatColor.YELLOW + "Leaderboard Summary", lore);
    }

    private ItemStack control(Material material, String name, String loreLine) {
        return control(material, name, Collections.singletonList(loreLine));
    }

    private ItemStack control(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(loreLines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pane(short data, String name) {
        ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE, 1, data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private PlayerStatsStore.LeaderboardMetric metricBefore(PlayerStatsStore.LeaderboardMetric metric) {
        PlayerStatsStore.LeaderboardMetric[] values = PlayerStatsStore.LeaderboardMetric.values();
        int idx = metric.ordinal() - 1;
        if (idx < 0) {
            idx = values.length - 1;
        }
        return values[idx];
    }

    private PlayerStatsStore.LeaderboardMetric metricAfter(PlayerStatsStore.LeaderboardMetric metric) {
        PlayerStatsStore.LeaderboardMetric[] values = PlayerStatsStore.LeaderboardMetric.values();
        int idx = metric.ordinal() + 1;
        if (idx >= values.length) {
            idx = 0;
        }
        return values[idx];
    }

    private PlayerStatsStore.LeaderboardPeriod periodAfter(PlayerStatsStore.LeaderboardPeriod period) {
        PlayerStatsStore.LeaderboardPeriod[] values = PlayerStatsStore.LeaderboardPeriod.values();
        int idx = period.ordinal() + 1;
        if (idx >= values.length) {
            idx = 0;
        }
        return values[idx];
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

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
