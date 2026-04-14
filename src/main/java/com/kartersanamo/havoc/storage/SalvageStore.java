package com.kartersanamo.havoc.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class SalvageStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public SalvageStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "salvage.yml");
    }

    public synchronized void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
        } else {
            yaml = YamlConfiguration.loadConfiguration(file);
        }
    }

    public synchronized void save() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save salvage.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
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
    }

    public synchronized void add(UUID id, int delta) {
        set(id, get(id) + delta);
    }
}
