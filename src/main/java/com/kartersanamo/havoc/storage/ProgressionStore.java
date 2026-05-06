package com.kartersanamo.havoc.storage;

import com.kartersanamo.havoc.base.BaseDifficulty;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressionStore {

    private final JavaPlugin plugin;
    private final File file;
    private final AsyncPersistenceQueue persistenceQueue;
    private DatabaseSupport database;
    private final Map<UUID, int[]> dbProgression = new HashMap<UUID, int[]>();
    private YamlConfiguration yaml;
    private boolean dirty;
    private boolean saveQueued;

    public ProgressionStore(JavaPlugin plugin, AsyncPersistenceQueue persistenceQueue) {
        this.plugin = plugin;
        this.persistenceQueue = persistenceQueue;
        this.file = new File(plugin.getDataFolder(), "progression.yml");
    }

    public synchronized void setDatabase(DatabaseSupport database) {
        this.database = database;
    }

    public synchronized void load() {
        dbProgression.clear();
        if (isDatabaseActive()) {
            loadFromDatabase();
            dirty = false;
            saveQueued = false;
            return;
        }
        if (!file.exists()) {
            yaml = new YamlConfiguration();
        } else {
            yaml = YamlConfiguration.loadConfiguration(file);
        }
        dirty = false;
        saveQueued = false;
    }

    public synchronized void save() {
        if (isDatabaseActive()) {
            saveSnapshotToDatabase(snapshotDbAndClearDirty());
            return;
        }
        String snap = snapshotAndClearDirty();
        saveSnapshot(snap);
    }

    private synchronized String snapshotAndClearDirty() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        dirty = false;
        return yaml.saveToString();
    }

    private void saveSnapshot(String snap) {
        try {
            YamlConfiguration out = new YamlConfiguration();
            out.loadFromString(snap == null ? "" : snap);
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save progression.yml: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not serialize progression.yml snapshot: " + e.getMessage());
        }
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
            String snap = null;
            Map<UUID, int[]> dbSnap = null;
            synchronized (this) {
                if (!dirty) {
                    saveQueued = false;
                    return;
                }
                if (isDatabaseActive()) {
                    dbSnap = snapshotDbAndClearDirty();
                } else {
                    snap = snapshotAndClearDirty();
                }
            }
            if (dbSnap != null) {
                saveSnapshotToDatabase(dbSnap);
            } else {
                saveSnapshot(snap);
            }
        }
    }

    public synchronized void resetAll() {
        if (isDatabaseActive()) {
            dbProgression.clear();
            dirty = true;
            return;
        }
        yaml = new YamlConfiguration();
        dirty = true;
    }

    /**
     * After a breach of {@code breached}, which difficulty coords should we point the player to next?
     */
    public synchronized BaseDifficulty nextHintDifficulty(UUID player, BaseDifficulty breached) {
        if (isDatabaseActive()) {
            return nextHintDifficultyDb(player, breached);
        }
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        String root = "players." + player.toString() + ".";
        int easy = yaml.getInt(root + "easy", 0);
        int med = yaml.getInt(root + "medium", 0);
        int hard = yaml.getInt(root + "hard", 0);
        if (breached == BaseDifficulty.EASY) {
            easy++;
            yaml.set(root + "easy", easy);
            dirty = true;
            if (easy % 3 == 0) {
                return BaseDifficulty.MEDIUM;
            }
            return BaseDifficulty.EASY;
        }
        if (breached == BaseDifficulty.MEDIUM) {
            med++;
            yaml.set(root + "medium", med);
            dirty = true;
            if (med % 3 == 0) {
                return BaseDifficulty.HARD;
            }
            return BaseDifficulty.MEDIUM;
        }
        hard++;
        yaml.set(root + "hard", hard);
        dirty = true;
        return BaseDifficulty.HARD;
    }

    private BaseDifficulty nextHintDifficultyDb(UUID player, BaseDifficulty breached) {
        int[] counts = dbProgression.get(player);
        if (counts == null) {
            counts = new int[]{0, 0, 0};
            dbProgression.put(player, counts);
        }
        if (breached == BaseDifficulty.EASY) {
            counts[0]++;
            dirty = true;
            if (counts[0] % 3 == 0) {
                return BaseDifficulty.MEDIUM;
            }
            return BaseDifficulty.EASY;
        }
        if (breached == BaseDifficulty.MEDIUM) {
            counts[1]++;
            dirty = true;
            if (counts[1] % 3 == 0) {
                return BaseDifficulty.HARD;
            }
            return BaseDifficulty.MEDIUM;
        }
        counts[2]++;
        dirty = true;
        return BaseDifficulty.HARD;
    }

    private boolean isDatabaseActive() {
        return database != null && database.isEnabled();
    }

    private void loadFromDatabase() {
        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement("SELECT player_uuid, easy_count, medium_count, hard_count FROM havoc_progression");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    dbProgression.put(id, new int[]{
                            Math.max(0, rs.getInt(2)),
                            Math.max(0, rs.getInt(3)),
                            Math.max(0, rs.getInt(4))
                    });
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (dbProgression.isEmpty() && file.exists()) {
                importYamlIntoDatabase();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load progression from database: " + e.getMessage());
        }
    }

    private void importYamlIntoDatabase() {
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        if (legacy == null || legacy.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : legacy.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int easy = Math.max(0, legacy.getInt("players." + key + ".easy", 0));
                int medium = Math.max(0, legacy.getInt("players." + key + ".medium", 0));
                int hard = Math.max(0, legacy.getInt("players." + key + ".hard", 0));
                dbProgression.put(id, new int[]{easy, medium, hard});
            } catch (IllegalArgumentException ignored) {
            }
        }
        saveSnapshotToDatabase(snapshotDbAndClearDirty());
        plugin.getLogger().info("Imported progression.yml data into MySQL (" + dbProgression.size() + " player rows).");
    }

    private Map<UUID, int[]> snapshotDbAndClearDirty() {
        synchronized (this) {
            Map<UUID, int[]> copy = new HashMap<UUID, int[]>();
            for (Map.Entry<UUID, int[]> e : dbProgression.entrySet()) {
                int[] v = e.getValue();
                copy.put(e.getKey(), new int[]{v[0], v[1], v[2]});
            }
            dirty = false;
            return copy;
        }
    }

    private void saveSnapshotToDatabase(Map<UUID, int[]> snapshot) {
        try (Connection c = database.openConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement clear = c.prepareStatement("DELETE FROM havoc_progression")) {
                clear.executeUpdate();
            }
            try (PreparedStatement up = c.prepareStatement(
                    "INSERT INTO havoc_progression (player_uuid, easy_count, medium_count, hard_count) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<UUID, int[]> e : snapshot.entrySet()) {
                    int[] v = e.getValue();
                    up.setString(1, e.getKey().toString());
                    up.setInt(2, Math.max(0, v[0]));
                    up.setInt(3, Math.max(0, v[1]));
                    up.setInt(4, Math.max(0, v[2]));
                    up.addBatch();
                }
                up.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save progression to database: " + e.getMessage());
        }
    }
}
