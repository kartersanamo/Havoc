package com.kartersanamo.havoc.generator;

import com.kartersanamo.havoc.base.BaseDifficulty;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Persists {@link BaseTemplateDefinition} as YAML under the plugin data folder.
 */
public final class BaseTemplateStore {

    private final JavaPlugin plugin;

    public BaseTemplateStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private File templatesDir() {
        return new File(plugin.getDataFolder(), "templates");
    }

    private File fileFor(BaseDifficulty d) {
        return new File(templatesDir(), d.name().toLowerCase(Locale.ROOT) + ".yml");
    }

    public BaseTemplateDefinition load(BaseDifficulty difficulty) {
        File f = fileFor(difficulty);
        if (!f.isFile()) {
            BaseTemplateDefinition def = BaseTemplateDefinition.defaultFor(difficulty);
            try {
                save(def);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write default template for " + difficulty, e);
            }
            return def;
        }
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        int size = c.getInt("size-chunks", 1);
        boolean slabs = c.getBoolean("slab-floor-between-walls", true);
        List<?> raw = c.getList("sections");
        List<DefenseSection> sections = new ArrayList<DefenseSection>();
        if (raw != null) {
            for (Object o : raw) {
                String typeName = null;
                int repeats = 1;
                if (o instanceof org.bukkit.configuration.ConfigurationSection) {
                    org.bukkit.configuration.ConfigurationSection sec = (org.bukkit.configuration.ConfigurationSection) o;
                    typeName = sec.getString("type", "FLAT_WALL");
                    repeats = sec.getInt("repeats", 1);
                } else if (o instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) o;
                    Object tObj = map.get("type");
                    Object rObj = map.get("repeats");
                    typeName = tObj != null ? tObj.toString() : "FLAT_WALL";
                    if (rObj instanceof Number) {
                        repeats = ((Number) rObj).intValue();
                    } else if (rObj != null) {
                        try {
                            repeats = Integer.parseInt(rObj.toString());
                        } catch (NumberFormatException ignored) {
                            repeats = 1;
                        }
                    }
                } else {
                    continue;
                }
                try {
                    DefenseType t = DefenseType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
                    sections.add(new DefenseSection(t, Math.max(1, repeats)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown defense type in template " + f.getName() + ": " + typeName);
                }
            }
        }
        if (sections.isEmpty()) {
            return BaseTemplateDefinition.defaultFor(difficulty);
        }
        return new BaseTemplateDefinition(difficulty, size, slabs, sections);
    }

    public void save(BaseTemplateDefinition def) throws IOException {
        File dir = templatesDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create templates directory: " + dir);
        }
        File f = fileFor(def.getDifficulty());
        FileConfiguration c = new YamlConfiguration();
        c.set("size-chunks", def.getSizeChunksOdd());
        c.set("slab-floor-between-walls", def.isSlabFloorBetweenWalls());
        List<java.util.Map<String, Object>> list = new ArrayList<java.util.Map<String, Object>>();
        for (DefenseSection s : def.getSections()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("type", s.getType().name());
            m.put("repeats", s.getRepeats());
            list.add(m);
        }
        c.set("sections", list);
        c.save(f);
    }
}
