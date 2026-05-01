package com.kartersanamo.havoc.storage;

import com.kartersanamo.havoc.base.BaseDifficulty;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class ProgressionStore {

    private final JavaPlugin plugin;
    private final File file;
    private final AsyncPersistenceQueue persistenceQueue;
    private YamlConfiguration yaml;
    private boolean dirty;
    private boolean saveQueued;

    public ProgressionStore(JavaPlugin plugin, AsyncPersistenceQueue persistenceQueue) {
        this.plugin = plugin;
        this.persistenceQueue = persistenceQueue;
        this.file = new File(plugin.getDataFolder(), "progression.yml");
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

    public synchronized void resetAll() {
        yaml = new YamlConfiguration();
        dirty = true;
    }

    /**
     * After a breach of {@code breached}, which difficulty coords should we point the player to next?
     */
    public synchronized BaseDifficulty nextHintDifficulty(UUID player, BaseDifficulty breached) {
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
}
