package com.kartersanamo.havoc.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class SalvageStore {

    private final JavaPlugin plugin;
    private final File file;
    private final AsyncPersistenceQueue persistenceQueue;
    private YamlConfiguration yaml;
    private boolean dirty;
    private boolean saveQueued;

    public SalvageStore(JavaPlugin plugin, AsyncPersistenceQueue persistenceQueue) {
        this.plugin = plugin;
        this.persistenceQueue = persistenceQueue;
        this.file = new File(plugin.getDataFolder(), "salvage.yml");
    }

    public synchronized void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
        } else {
            yaml = YamlConfiguration.loadConfiguration(file);
        }
        dirty = false;
        saveQueued = false;
    }

    public synchronized void save() {
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
            String snap;
            synchronized (this) {
                if (!dirty) {
                    saveQueued = false;
                    return;
                }
                snap = snapshotAndClearDirty();
            }
            saveSnapshot(snap);
        }
    }

    public synchronized int get(UUID id) {
        if (yaml == null) {
            return 0;
        }
        return yaml.getInt("players." + id.toString(), 0);
    }

    public synchronized void set(UUID id, int amount) {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("players." + id.toString(), Math.max(0, amount));
        dirty = true;
    }

    public synchronized void add(UUID id, int delta) {
        set(id, get(id) + delta);
    }
}
