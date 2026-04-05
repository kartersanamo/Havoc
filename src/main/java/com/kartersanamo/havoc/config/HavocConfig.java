package com.kartersanamo.havoc.config;

import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HavocConfig {

    private final JavaPlugin plugin;
    private String worldName;
    private String havocFactionTag;
    private int borderHalfSize;
    private EnumMap<BaseDifficulty, Integer> basesPerDifficulty = new EnumMap<BaseDifficulty, Integer>(BaseDifficulty.class);
    private int minCenterSeparationChunks;
    private int watchChunkRadius;
    private int restoreSeconds;
    private int pasteCenterWorldY;
    private boolean debugBroadcastGame;
    private EnumMap<BaseDifficulty, int[]> schematicCenterFromMin = new EnumMap<BaseDifficulty, int[]>(BaseDifficulty.class);
    private String schematicsFolder;
    private String easySchematic;
    private String mediumSchematic;
    private String hardSchematic;
    private int salvageEasyMin;
    private int salvageEasyMax;
    private int salvageMediumMin;
    private int salvageMediumMax;
    private int salvageHardMin;
    private int salvageHardMax;
    private int rewardRadius;
    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private int shopRows;
    private List<ShopItem> shopItems = new ArrayList<ShopItem>();
    private Set<Material> breachMaterials = new HashSet<Material>();

    public HavocConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        worldName = c.getString("world-name", "world");
        havocFactionTag = c.getString("havoc-faction-tag", "Havoc");
        borderHalfSize = c.getInt("border-half-size", 2500);
        basesPerDifficulty.clear();
        ConfigurationSection bpd = c.getConfigurationSection("bases-per-difficulty");
        if (bpd != null) {
            for (String k : bpd.getKeys(false)) {
                try {
                    BaseDifficulty d = BaseDifficulty.valueOf(k.toUpperCase(Locale.ROOT));
                    basesPerDifficulty.put(d, bpd.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (basesPerDifficulty.isEmpty()) {
            basesPerDifficulty.put(BaseDifficulty.EASY, 3);
            basesPerDifficulty.put(BaseDifficulty.MEDIUM, 2);
            basesPerDifficulty.put(BaseDifficulty.HARD, 1);
        }
        minCenterSeparationChunks = c.getInt("min-center-separation-chunks", 20);
        watchChunkRadius = c.getInt("watch-chunk-radius", 5);
        restoreSeconds = c.getInt("restore-seconds", 1800);
        debugBroadcastGame = c.getBoolean("debug-broadcast-game", true);
        pasteCenterWorldY = c.getInt("paste-center-world-y", c.getInt("paste-floor-y", 64));
        schematicCenterFromMin.clear();
        ConfigurationSection sc = c.getConfigurationSection("schematic-center-from-min");
        if (sc != null) {
            for (String k : sc.getKeys(false)) {
                try {
                    BaseDifficulty d = BaseDifficulty.valueOf(k.toUpperCase(Locale.ROOT));
                    schematicCenterFromMin.put(d, parseTriple(sc.getString(k), 8, 0, 8));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (BaseDifficulty d : BaseDifficulty.values()) {
            if (!schematicCenterFromMin.containsKey(d)) {
                schematicCenterFromMin.put(d, new int[]{8, 0, 8});
            }
        }
        schematicsFolder = c.getString("schematics-folder", "schematics");
        easySchematic = c.getString("easy-schematic", "EasyBase.schematic");
        mediumSchematic = c.getString("medium-schematic", "MediumBase.schematic");
        hardSchematic = c.getString("hard-schematic", "HardBase.schematic");
        ConfigurationSection sr = c.getConfigurationSection("salvage-reward");
        if (sr != null) {
            salvageEasyMin = sr.getInt("EASY_MIN", 15);
            salvageEasyMax = sr.getInt("EASY_MAX", 45);
            salvageMediumMin = sr.getInt("MEDIUM_MIN", 40);
            salvageMediumMax = sr.getInt("MEDIUM_MAX", 120);
            salvageHardMin = sr.getInt("HARD_MIN", 100);
            salvageHardMax = sr.getInt("HARD_MAX", 300);
        }
        rewardRadius = c.getInt("reward-radius", 32);
        ConfigurationSection sp = c.getConfigurationSection("spawn");
        if (sp != null) {
            spawnWorld = sp.getString("world", worldName);
            spawnX = sp.getDouble("x", 0.5);
            spawnY = sp.getDouble("y", 64);
            spawnZ = sp.getDouble("z", 0.5);
        } else {
            spawnWorld = worldName;
            spawnX = 0.5;
            spawnY = 64;
            spawnZ = 0.5;
        }
        ConfigurationSection shop = c.getConfigurationSection("shop");
        shopItems.clear();
        shopRows = 3;
        if (shop != null) {
            shopRows = Math.min(6, Math.max(1, shop.getInt("rows", 3)));
            List<?> raw = shop.getList("items");
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) o;
                        shopItems.add(parseShopItemMap(m));
                    }
                }
            }
        }
        breachMaterials.clear();
        List<String> mats = c.getStringList("breach-materials");
        if (mats.isEmpty()) {
            breachMaterials.add(Material.OBSIDIAN);
            breachMaterials.add(Material.STATIONARY_WATER);
            breachMaterials.add(Material.WATER);
        } else {
            for (String name : mats) {
                try {
                    breachMaterials.add(Material.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown breach material: " + name);
                }
            }
        }
    }

    private ShopItem parseShopItemMap(Map<String, Object> m) {
        int slot = intVal(m.get("slot"), 0);
        String matName = String.valueOf(m.getOrDefault("material", "STONE"));
        Material mat = Material.valueOf(matName.toUpperCase(Locale.ROOT));
        int data = intVal(m.get("data"), 0);
        int amount = intVal(m.get("amount"), 1);
        int price = intVal(m.get("price"), 0);
        String dn = ChatColor.translateAlternateColorCodes('&', String.valueOf(m.getOrDefault("display-name", mat.name())));
        return new ShopItem(slot, mat, (byte) data, amount, price, dn);
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        return def;
    }

    private static int[] parseTriple(String raw, int defX, int defY, int defZ) {
        if (raw == null || raw.trim().isEmpty()) {
            return new int[]{defX, defY, defZ};
        }
        String[] p = raw.trim().split("\\s*,\\s*");
        if (p.length != 3) {
            return new int[]{defX, defY, defZ};
        }
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return new int[]{defX, defY, defZ};
        }
    }

    public String schematicFileName(BaseDifficulty d) {
        switch (d) {
            case MEDIUM:
                return mediumSchematic;
            case HARD:
                return hardSchematic;
            default:
                return easySchematic;
        }
    }

    public int basesToSpawn(BaseDifficulty d) {
        Integer n = basesPerDifficulty.get(d);
        return n == null ? 0 : Math.max(0, n);
    }

    public String getWorldName() {
        return worldName;
    }

    public String getHavocFactionTag() {
        return havocFactionTag;
    }

    public int getBorderHalfSize() {
        return borderHalfSize;
    }

    public int getMinCenterSeparationChunks() {
        return minCenterSeparationChunks;
    }

    public int getWatchChunkRadius() {
        return watchChunkRadius;
    }

    public int getRestoreSeconds() {
        return restoreSeconds;
    }

    /**
     * World Y of the obsidian column center block (see schematic-center-from-min dy).
     */
    public int getPasteCenterWorldY() {
        return pasteCenterWorldY;
    }

    /** @deprecated use {@link #getPasteCenterWorldY()} */
    public int getPasteFloorY() {
        return pasteCenterWorldY;
    }

    public boolean isDebugBroadcastGame() {
        return debugBroadcastGame;
    }

    public int[] getSchematicCenterOffset(BaseDifficulty d) {
        int[] t = schematicCenterFromMin.get(d);
        return t == null ? new int[]{8, 0, 8} : t;
    }

    public String getSchematicsFolder() {
        return schematicsFolder;
    }

    public int getRewardRadius() {
        return rewardRadius;
    }

    public int randomSalvage(BaseDifficulty d) {
        int lo;
        int hi;
        switch (d) {
            case MEDIUM:
                lo = salvageMediumMin;
                hi = salvageMediumMax;
                break;
            case HARD:
                lo = salvageHardMin;
                hi = salvageHardMax;
                break;
            default:
                lo = salvageEasyMin;
                hi = salvageEasyMax;
                break;
        }
        if (hi < lo) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        return lo + (int) (Math.random() * (hi - lo + 1));
    }

    public org.bukkit.Location getSpawnLocation() {
        org.bukkit.World w = Bukkit.getWorld(spawnWorld);
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
        }
        return new org.bukkit.Location(w, spawnX, spawnY, spawnZ);
    }

    public int getShopRows() {
        return shopRows;
    }

    public List<ShopItem> getShopItems() {
        return shopItems;
    }

    public Set<Material> getBreachMaterials() {
        return breachMaterials;
    }
}
