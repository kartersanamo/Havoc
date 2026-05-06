package com.kartersanamo.havoc.storage;

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

public final class SalvageStore {

    private final JavaPlugin plugin;
    private final File file;
    private final AsyncPersistenceQueue persistenceQueue;
    private DatabaseSupport database;
    private final Map<UUID, Integer> dbBalances = new HashMap<UUID, Integer>();
    private YamlConfiguration yaml;
    private boolean dirty;
    private boolean saveQueued;

    public SalvageStore(JavaPlugin plugin, AsyncPersistenceQueue persistenceQueue) {
        this.plugin = plugin;
        this.persistenceQueue = persistenceQueue;
        this.file = new File(plugin.getDataFolder(), "salvage.yml");
    }

    public synchronized void setDatabase(DatabaseSupport database) {
        this.database = database;
    }

    public synchronized void load() {
        dbBalances.clear();
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
            saveSnapshotToDatabase(new HashMap<UUID, Integer>(dbBalances));
            dirty = false;
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
            plugin.getLogger().warning("Could not save salvage.yml: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not serialize salvage.yml snapshot: " + e.getMessage());
        }
    }

    public synchronized int get(UUID id) {
        if (isDatabaseActive()) {
            Integer amount = dbBalances.get(id);
            return amount == null ? 0 : amount.intValue();
        }
        if (yaml == null) {
            return 0;
        }
        return yaml.getInt("players." + id.toString(), 0);
    }

    public synchronized void set(UUID id, int amount) {
        if (isDatabaseActive()) {
            dbBalances.put(id, Integer.valueOf(Math.max(0, amount)));
            dirty = true;
            return;
        }
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("players." + id.toString(), Math.max(0, amount));
        dirty = true;
    }

    public synchronized void add(UUID id, int delta) {
        set(id, get(id) + delta);
    }

    private boolean isDatabaseActive() {
        return database != null && database.isEnabled();
    }

    private void loadFromDatabase() {
        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement("SELECT player_uuid, amount FROM havoc_salvage");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String raw = rs.getString(1);
                try {
                    dbBalances.put(UUID.fromString(raw), Integer.valueOf(Math.max(0, rs.getInt(2))));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (dbBalances.isEmpty() && file.exists()) {
                importYamlIntoDatabase();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load salvage from database: " + e.getMessage());
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
                dbBalances.put(id, Integer.valueOf(Math.max(0, legacy.getInt("players." + key, 0))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        saveSnapshotToDatabase(new HashMap<UUID, Integer>(dbBalances));
        plugin.getLogger().info("Imported salvage.yml data into MySQL (" + dbBalances.size() + " player rows).");
    }

    private void saveSnapshotToDatabase(Map<UUID, Integer> snapshot) {
        try (Connection c = database.openConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement clear = c.prepareStatement("DELETE FROM havoc_salvage")) {
                clear.executeUpdate();
            }
            try (PreparedStatement up = c.prepareStatement("INSERT INTO havoc_salvage (player_uuid, amount) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Integer> e : snapshot.entrySet()) {
                    up.setString(1, e.getKey().toString());
                    up.setInt(2, Math.max(0, e.getValue().intValue()));
                    up.addBatch();
                }
                up.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save salvage to database: " + e.getMessage());
        }
    }

    private Map<UUID, Integer> snapshotDbAndClearDirty() {
        synchronized (this) {
            dirty = false;
            return new HashMap<UUID, Integer>(dbBalances);
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
            Map<UUID, Integer> dbSnap = null;
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
}
