package com.kartersanamo.havoc.stats;

import com.kartersanamo.havoc.storage.AsyncPersistenceQueue;
import com.kartersanamo.havoc.storage.DatabaseSupport;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class PlayerStatsStore {

    public enum LeaderboardMetric {
        SALVAGE_EARNED("Salvage Earned"),
        SALVAGE_SPENT("Salvage Spent"),
        BREACHES_PARTICIPATED("Breaches Participated"),
        BREACHES_TRIGGERED("Breaches Triggered"),
        SHOP_PURCHASES("Shop Purchases");

        public final String label;

        LeaderboardMetric(String label) {
            this.label = label;
        }
    }

    public enum LeaderboardPeriod {
        LIFETIME("Lifetime"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly");

        public final String label;

        LeaderboardPeriod(String label) {
            this.label = label;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final AsyncPersistenceQueue persistenceQueue;
    private final Map<UUID, PlayerStats> lifetimeStatsByPlayer = new HashMap<UUID, PlayerStats>();
    private final Map<UUID, PlayerStats> weeklyStatsByPlayer = new HashMap<UUID, PlayerStats>();
    private final Map<UUID, PlayerStats> monthlyStatsByPlayer = new HashMap<UUID, PlayerStats>();
    private DatabaseSupport database;
    private String weeklyCycleKey = "";
    private String monthlyCycleKey = "";
    private boolean dirty;
    private boolean saveQueued;

    public PlayerStatsStore(JavaPlugin plugin, AsyncPersistenceQueue persistenceQueue) {
        this.plugin = plugin;
        this.persistenceQueue = persistenceQueue;
        this.file = new File(plugin.getDataFolder(), "player-stats.yml");
    }

    public synchronized void setDatabase(DatabaseSupport database) {
        this.database = database;
    }

    public synchronized void load() {
        lifetimeStatsByPlayer.clear();
        weeklyStatsByPlayer.clear();
        monthlyStatsByPlayer.clear();
        if (isDatabaseActive()) {
            loadFromDatabase();
            if (lifetimeStatsByPlayer.isEmpty() && file.exists()) {
                importYamlIntoMemory();
                saveSnapshotToDatabase(snapshotAndClearDirty());
                plugin.getLogger().info("Imported player-stats.yml into MySQL (" + lifetimeStatsByPlayer.size() + " player rows).");
            }
        } else {
            importYamlIntoMemory();
        }
        ensureRollingWindowsCurrent();
        dirty = false;
        saveQueued = false;
    }

    public synchronized void save() {
        if (isDatabaseActive()) {
            saveSnapshotToDatabase(snapshotAndClearDirty());
            return;
        }
        saveSnapshotToYaml(snapshotAndClearDirty());
    }

    public synchronized PlayerStats get(UUID playerId) {
        ensureRollingWindowsCurrent();
        PlayerStats stats = lifetimeStatsByPlayer.get(playerId);
        if (stats == null) {
            stats = new PlayerStats();
            lifetimeStatsByPlayer.put(playerId, stats);
        }
        return copy(stats);
    }

    public synchronized void addBreachParticipation(UUID playerId, int salvageAmount) {
        ensureRollingWindowsCurrent();
        addBreachParticipation(getMutable(lifetimeStatsByPlayer, playerId), salvageAmount);
        addBreachParticipation(getMutable(weeklyStatsByPlayer, playerId), salvageAmount);
        addBreachParticipation(getMutable(monthlyStatsByPlayer, playerId), salvageAmount);
        dirty = true;
    }

    public synchronized void addBreachTrigger(UUID playerId) {
        ensureRollingWindowsCurrent();
        addBreachTrigger(getMutable(lifetimeStatsByPlayer, playerId));
        addBreachTrigger(getMutable(weeklyStatsByPlayer, playerId));
        addBreachTrigger(getMutable(monthlyStatsByPlayer, playerId));
        dirty = true;
    }

    public synchronized void addShopPurchase(UUID playerId, int spentAmount) {
        ensureRollingWindowsCurrent();
        addShopPurchase(getMutable(lifetimeStatsByPlayer, playerId), spentAmount);
        addShopPurchase(getMutable(weeklyStatsByPlayer, playerId), spentAmount);
        addShopPurchase(getMutable(monthlyStatsByPlayer, playerId), spentAmount);
        dirty = true;
    }

    public synchronized List<Map.Entry<UUID, PlayerStats>> top(LeaderboardMetric metric, LeaderboardPeriod period, int limit) {
        ensureRollingWindowsCurrent();
        Map<UUID, PlayerStats> source = sourceMap(period);
        List<Map.Entry<UUID, PlayerStats>> rows = new ArrayList<Map.Entry<UUID, PlayerStats>>();
        for (Map.Entry<UUID, PlayerStats> e : source.entrySet()) {
            rows.add(new java.util.AbstractMap.SimpleEntry<UUID, PlayerStats>(e.getKey(), copy(e.getValue())));
        }
        rows.sort(new Comparator<Map.Entry<UUID, PlayerStats>>() {
            @Override
            public int compare(Map.Entry<UUID, PlayerStats> a, Map.Entry<UUID, PlayerStats> b) {
                int av = metricValue(a.getValue(), metric);
                int bv = metricValue(b.getValue(), metric);
                if (av != bv) {
                    return Integer.compare(bv, av);
                }
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });
        if (rows.size() > limit) {
            return new ArrayList<Map.Entry<UUID, PlayerStats>>(rows.subList(0, limit));
        }
        return rows;
    }

    public void saveAsync() {
        synchronized (this) {
            dirty = true;
            if (saveQueued) {
                return;
            }
            saveQueued = true;
        }
        persistenceQueue.submit(new Runnable() {
            @Override
            public void run() {
                flushQueuedSaves();
            }
        });
    }

    private void flushQueuedSaves() {
        while (true) {
            Map<UUID, PlayerStats> snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveQueued = false;
                    return;
                }
                snapshot = snapshotAndClearDirty();
            }
            if (isDatabaseActive()) {
                saveSnapshotToDatabase(snapshot);
            } else {
                saveSnapshotToYaml(snapshot);
            }
        }
    }

    private synchronized Map<UUID, PlayerStats> snapshotAndClearDirty() {
        Map<UUID, PlayerStats> lifetimeCopy = new HashMap<UUID, PlayerStats>();
        for (Map.Entry<UUID, PlayerStats> e : lifetimeStatsByPlayer.entrySet()) {
            lifetimeCopy.put(e.getKey(), copy(e.getValue()));
        }
        Map<UUID, PlayerStats> weeklyCopy = new HashMap<UUID, PlayerStats>();
        for (Map.Entry<UUID, PlayerStats> e : weeklyStatsByPlayer.entrySet()) {
            weeklyCopy.put(e.getKey(), copy(e.getValue()));
        }
        Map<UUID, PlayerStats> monthlyCopy = new HashMap<UUID, PlayerStats>();
        for (Map.Entry<UUID, PlayerStats> e : monthlyStatsByPlayer.entrySet()) {
            monthlyCopy.put(e.getKey(), copy(e.getValue()));
        }
        Map<UUID, PlayerStats> merged = new HashMap<UUID, PlayerStats>();
        merged.putAll(lifetimeCopy);
        dirty = false;
        SnapshotContext.set(weeklyCopy, monthlyCopy, weeklyCycleKey, monthlyCycleKey);
        return merged;
    }

    private synchronized PlayerStats getMutable(Map<UUID, PlayerStats> source, UUID playerId) {
        PlayerStats stats = source.get(playerId);
        if (stats == null) {
            stats = new PlayerStats();
            source.put(playerId, stats);
        }
        return stats;
    }

    private static void addBreachParticipation(PlayerStats stats, int salvageAmount) {
        stats.breachesParticipated += 1;
        stats.salvageEarned += Math.max(0, salvageAmount);
    }

    private static void addBreachTrigger(PlayerStats stats) {
        stats.breachesTriggered += 1;
    }

    private static void addShopPurchase(PlayerStats stats, int spentAmount) {
        stats.shopPurchases += 1;
        stats.salvageSpent += Math.max(0, spentAmount);
    }

    private static PlayerStats copy(PlayerStats src) {
        PlayerStats out = new PlayerStats();
        out.breachesParticipated = src.breachesParticipated;
        out.breachesTriggered = src.breachesTriggered;
        out.salvageEarned = src.salvageEarned;
        out.salvageSpent = src.salvageSpent;
        out.shopPurchases = src.shopPurchases;
        return out;
    }

    private static int metricValue(PlayerStats stats, LeaderboardMetric metric) {
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

    private boolean isDatabaseActive() {
        return database != null && database.isEnabled();
    }

    private void importYamlIntoMemory() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        weeklyCycleKey = yaml.getString("rolling.weekly-cycle-key", "");
        monthlyCycleKey = yaml.getString("rolling.monthly-cycle-key", "");
        importSection(yaml.getConfigurationSection("lifetime"), lifetimeStatsByPlayer);
        importSection(yaml.getConfigurationSection("weekly"), weeklyStatsByPlayer);
        importSection(yaml.getConfigurationSection("monthly"), monthlyStatsByPlayer);
        if (lifetimeStatsByPlayer.isEmpty()) {
            importSection(yaml.getConfigurationSection("players"), lifetimeStatsByPlayer);
        }
    }

    private void saveSnapshotToYaml(Map<UUID, PlayerStats> snapshot) {
        YamlConfiguration out = new YamlConfiguration();
        Map<UUID, PlayerStats> weeklySnapshot = SnapshotContext.getWeekly();
        Map<UUID, PlayerStats> monthlySnapshot = SnapshotContext.getMonthly();
        out.set("rolling.weekly-cycle-key", SnapshotContext.getWeeklyKey());
        out.set("rolling.monthly-cycle-key", SnapshotContext.getMonthlyKey());
        writeSection(out, "lifetime", snapshot);
        writeSection(out, "weekly", weeklySnapshot);
        writeSection(out, "monthly", monthlySnapshot);
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player-stats.yml: " + e.getMessage());
        }
    }

    private void loadFromDatabase() {
        readMetaFromDatabase();
        readTableInto("havoc_player_stats", lifetimeStatsByPlayer);
        readTableInto("havoc_player_stats_weekly", weeklyStatsByPlayer);
        readTableInto("havoc_player_stats_monthly", monthlyStatsByPlayer);
    }

    private void saveSnapshotToDatabase(Map<UUID, PlayerStats> lifetimeSnapshot) {
        Map<UUID, PlayerStats> weeklySnapshot = SnapshotContext.getWeekly();
        Map<UUID, PlayerStats> monthlySnapshot = SnapshotContext.getMonthly();
        try (Connection c = database.openConnection()) {
            c.setAutoCommit(false);
            rewriteTable(c, "havoc_player_stats", lifetimeSnapshot);
            rewriteTable(c, "havoc_player_stats_weekly", weeklySnapshot);
            rewriteTable(c, "havoc_player_stats_monthly", monthlySnapshot);
            writeMeta(c, "weekly_cycle_key", SnapshotContext.getWeeklyKey());
            writeMeta(c, "monthly_cycle_key", SnapshotContext.getMonthlyKey());
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save player stats to database: " + e.getMessage());
        }
    }

    private void readMetaFromDatabase() {
        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement("SELECT meta_key, meta_value FROM havoc_stats_meta");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String k = rs.getString(1);
                String v = rs.getString(2);
                if ("weekly_cycle_key".equalsIgnoreCase(k)) {
                    weeklyCycleKey = v == null ? "" : v;
                } else if ("monthly_cycle_key".equalsIgnoreCase(k)) {
                    monthlyCycleKey = v == null ? "" : v;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not read stats metadata: " + e.getMessage());
        }
    }

    private void readTableInto(String tableName, Map<UUID, PlayerStats> out) {
        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_uuid, breaches_participated, breaches_triggered, salvage_earned, salvage_spent, shop_purchases FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    PlayerStats stats = new PlayerStats();
                    stats.breachesParticipated = Math.max(0, rs.getInt(2));
                    stats.breachesTriggered = Math.max(0, rs.getInt(3));
                    stats.salvageEarned = Math.max(0, rs.getInt(4));
                    stats.salvageSpent = Math.max(0, rs.getInt(5));
                    stats.shopPurchases = Math.max(0, rs.getInt(6));
                    out.put(id, stats);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load player stats from " + tableName + ": " + e.getMessage());
        }
    }

    private void rewriteTable(Connection c, String tableName, Map<UUID, PlayerStats> snapshot) throws SQLException {
        try (PreparedStatement clear = c.prepareStatement("DELETE FROM " + tableName)) {
            clear.executeUpdate();
        }
        try (PreparedStatement up = c.prepareStatement(
                "INSERT INTO " + tableName + " (player_uuid, breaches_participated, breaches_triggered, salvage_earned, salvage_spent, shop_purchases) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<UUID, PlayerStats> e : snapshot.entrySet()) {
                PlayerStats stats = e.getValue();
                up.setString(1, e.getKey().toString());
                up.setInt(2, Math.max(0, stats.breachesParticipated));
                up.setInt(3, Math.max(0, stats.breachesTriggered));
                up.setInt(4, Math.max(0, stats.salvageEarned));
                up.setInt(5, Math.max(0, stats.salvageSpent));
                up.setInt(6, Math.max(0, stats.shopPurchases));
                up.addBatch();
            }
            up.executeBatch();
        }
    }

    private void writeMeta(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO havoc_stats_meta (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value)")) {
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
        }
    }

    private void importSection(ConfigurationSection section, Map<UUID, PlayerStats> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                PlayerStats stats = new PlayerStats();
                String root = key + ".";
                stats.breachesParticipated = Math.max(0, section.getInt(root + "breaches-participated", 0));
                stats.breachesTriggered = Math.max(0, section.getInt(root + "breaches-triggered", 0));
                stats.salvageEarned = Math.max(0, section.getInt(root + "salvage-earned", 0));
                stats.salvageSpent = Math.max(0, section.getInt(root + "salvage-spent", 0));
                stats.shopPurchases = Math.max(0, section.getInt(root + "shop-purchases", 0));
                target.put(id, stats);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void writeSection(YamlConfiguration out, String section, Map<UUID, PlayerStats> data) {
        if (data == null) {
            return;
        }
        for (Map.Entry<UUID, PlayerStats> e : data.entrySet()) {
            String root = section + "." + e.getKey().toString() + ".";
            PlayerStats stats = e.getValue();
            out.set(root + "breaches-participated", Math.max(0, stats.breachesParticipated));
            out.set(root + "breaches-triggered", Math.max(0, stats.breachesTriggered));
            out.set(root + "salvage-earned", Math.max(0, stats.salvageEarned));
            out.set(root + "salvage-spent", Math.max(0, stats.salvageSpent));
            out.set(root + "shop-purchases", Math.max(0, stats.shopPurchases));
        }
    }

    private Map<UUID, PlayerStats> sourceMap(LeaderboardPeriod period) {
        switch (period) {
            case WEEKLY:
                return weeklyStatsByPlayer;
            case MONTHLY:
                return monthlyStatsByPlayer;
            case LIFETIME:
            default:
                return lifetimeStatsByPlayer;
        }
    }

    private void ensureRollingWindowsCurrent() {
        ZoneId zone = zoneId();
        ZonedDateTime now = ZonedDateTime.now(zone);
        WeekFields wf = WeekFields.ISO;
        String currentWeekly = now.get(wf.weekBasedYear()) + "-W" + now.get(wf.weekOfWeekBasedYear());
        String currentMonthly = now.getYear() + "-" + String.format(Locale.US, "%02d", now.getMonthValue());
        boolean changed = false;
        if (!currentWeekly.equals(weeklyCycleKey)) {
            weeklyCycleKey = currentWeekly;
            weeklyStatsByPlayer.clear();
            changed = true;
        }
        if (!currentMonthly.equals(monthlyCycleKey)) {
            monthlyCycleKey = currentMonthly;
            monthlyStatsByPlayer.clear();
            changed = true;
        }
        if (changed) {
            dirty = true;
        }
    }

    private ZoneId zoneId() {
        String raw = plugin.getConfig().getString("timers.progression-reset-timezone", "America/New_York");
        try {
            return ZoneId.of(raw);
        } catch (Exception ignored) {
            return ZoneId.of("America/New_York");
        }
    }

    private static final class SnapshotContext {
        private static final ThreadLocal<Map<UUID, PlayerStats>> WEEKLY = new ThreadLocal<Map<UUID, PlayerStats>>();
        private static final ThreadLocal<Map<UUID, PlayerStats>> MONTHLY = new ThreadLocal<Map<UUID, PlayerStats>>();
        private static final ThreadLocal<String> WEEKLY_KEY = new ThreadLocal<String>();
        private static final ThreadLocal<String> MONTHLY_KEY = new ThreadLocal<String>();

        private SnapshotContext() {
        }

        static void set(Map<UUID, PlayerStats> weekly, Map<UUID, PlayerStats> monthly, String weeklyKey, String monthlyKey) {
            WEEKLY.set(weekly);
            MONTHLY.set(monthly);
            WEEKLY_KEY.set(weeklyKey);
            MONTHLY_KEY.set(monthlyKey);
        }

        static Map<UUID, PlayerStats> getWeekly() {
            Map<UUID, PlayerStats> map = WEEKLY.get();
            return map == null ? new HashMap<UUID, PlayerStats>() : map;
        }

        static Map<UUID, PlayerStats> getMonthly() {
            Map<UUID, PlayerStats> map = MONTHLY.get();
            return map == null ? new HashMap<UUID, PlayerStats>() : map;
        }

        static String getWeeklyKey() {
            String k = WEEKLY_KEY.get();
            return k == null ? "" : k;
        }

        static String getMonthlyKey() {
            String k = MONTHLY_KEY.get();
            return k == null ? "" : k;
        }
    }
}
