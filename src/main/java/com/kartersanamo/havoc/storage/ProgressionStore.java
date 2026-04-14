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
    private YamlConfiguration yaml;

    public ProgressionStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progression.yml");
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
            plugin.getLogger().warning("Could not save progression.yml: " + e.getMessage());
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
            if (easy % 3 == 0) {
                return BaseDifficulty.MEDIUM;
            }
            return BaseDifficulty.EASY;
        }
        if (breached == BaseDifficulty.MEDIUM) {
            med++;
            yaml.set(root + "medium", med);
            if (med % 3 == 0) {
                return BaseDifficulty.HARD;
            }
            return BaseDifficulty.MEDIUM;
        }
        hard++;
        yaml.set(root + "hard", hard);
        return BaseDifficulty.HARD;
    }
}
