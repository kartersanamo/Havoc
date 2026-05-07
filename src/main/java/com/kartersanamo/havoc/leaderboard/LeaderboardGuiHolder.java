package com.kartersanamo.havoc.leaderboard;

import com.kartersanamo.havoc.stats.PlayerStatsStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class LeaderboardGuiHolder implements InventoryHolder {
    final PlayerStatsStore.LeaderboardMetric metric;
    final PlayerStatsStore.LeaderboardPeriod period;
    final int page;

    LeaderboardGuiHolder(PlayerStatsStore.LeaderboardMetric metric, PlayerStatsStore.LeaderboardPeriod period, int page) {
        this.metric = metric;
        this.period = period;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
