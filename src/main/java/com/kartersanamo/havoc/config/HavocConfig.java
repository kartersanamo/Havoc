package com.kartersanamo.havoc.config;

import com.kartersanamo.havoc.base.BaseDifficulty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private boolean worldeditSchematicOffsetAdd;
    private int[] pasteExtraWorldDelta = new int[]{0, 0, 0};
    private boolean verticalPasteSnapToBedrock;
    /** 0..15: world block within chunk where schematic-center-from-min sits horizontally (bedrock sample uses same column). */
    private int chunkCenterLocalX = 6;
    private int chunkCenterLocalZ = 6;
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
    private int maintainerInitialDelayTicks;
    private int maintainerPeriodTicks;
    private int restoreTickerPeriodTicks;
    private int spawnWorkerPeriodTicks;
    private int spawnMaxAttempts;
    private int spawnBorderPaddingBlocks;
    private int spawnSearchAttemptsPerTick;
    private int spawnPreloadChunksPerTick;
    private int spawnSnapshotColumnsPerTick;
    private int spawnPasteColumnsPerTick;
    private int spawnClaimChunksPerTick;
    private int minBaseSeparationBlocks;
    private int minOriginDistanceBlocks;
    private long lockNotifyCooldownMs;
    private String progressionResetTimezone;
    private int maxLogLines;
    private int maxLogDays;
    private boolean archiveLogsOnRotate;
    private Set<Material> breachMaterials = new HashSet<Material>();

    public HavocConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        worldName = c.getString("world-name", "Havoc");
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
        worldeditSchematicOffsetAdd = c.getBoolean("worldedit-schematic-offset-add", true);
        pasteExtraWorldDelta = parseTriple(c.getString("schematic-paste-extra-world-delta"), 0, 0, 0);
        int[] chunkLocal = parsePair(c.getString("chunk-center-local-xz"), 6, 6);
        chunkCenterLocalX = clampChunkLocal(chunkLocal[0]);
        chunkCenterLocalZ = clampChunkLocal(chunkLocal[1]);
        String vMode = c.getString("vertical-paste-mode", "SNAP_BOTTOM_TO_BEDROCK");
        verticalPasteSnapToBedrock = !"USE_CONFIG_Y".equalsIgnoreCase(vMode.trim());
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
        maintainerInitialDelayTicks = Math.max(1, c.getInt("timers.maintainer-initial-delay-ticks", 40));
        maintainerPeriodTicks = Math.max(1, c.getInt("timers.maintainer-period-ticks", 200));
        restoreTickerPeriodTicks = Math.max(1, c.getInt("timers.restore-ticker-period-ticks", 1));
        spawnWorkerPeriodTicks = Math.max(1, c.getInt("timers.spawn-worker-period-ticks", 1));
        spawnMaxAttempts = Math.max(1, c.getInt("placement.spawn-max-attempts", 80));
        spawnBorderPaddingBlocks = Math.max(0, c.getInt("placement.spawn-border-padding-blocks", 32));
        spawnSearchAttemptsPerTick = Math.max(1, c.getInt("placement.spawn-search-attempts-per-tick", 2));
        spawnPreloadChunksPerTick = Math.max(1, c.getInt("performance.spawn-preload-chunks-per-tick", 8));
        spawnSnapshotColumnsPerTick = Math.max(1, c.getInt("performance.spawn-snapshot-columns-per-tick", 8));
        spawnPasteColumnsPerTick = Math.max(1, c.getInt("performance.spawn-paste-columns-per-tick", 4));
        spawnClaimChunksPerTick = Math.max(1, c.getInt("performance.spawn-claim-chunks-per-tick", 6));
        minBaseSeparationBlocks = Math.max(0, c.getInt("placement.min-base-separation-blocks", 500));
        minOriginDistanceBlocks = Math.max(0, c.getInt("placement.min-origin-distance-blocks", 500));
        lockNotifyCooldownMs = Math.max(0L, c.getLong("timers.lock-notify-cooldown-ms", 1500L));
        progressionResetTimezone = c.getString("timers.progression-reset-timezone", "America/New_York");
        maxLogLines = Math.max(100, c.getInt("logs.max-log-lines", 100000));
        maxLogDays = Math.max(1, c.getInt("logs.max-log-days", 30));
        archiveLogsOnRotate = c.getBoolean("logs.archive-on-rotate", true);
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
        normalizeWorldNames();
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

    /**
     * Bases must not use the literal default "world" — multiverse Havoc world is required.
     */
    private void normalizeWorldNames() {
        String w = worldName == null ? "" : worldName.trim();
        if (w.isEmpty() || "world".equalsIgnoreCase(w)) {
            plugin.getLogger().warning("Havoc: world-name was \"" + worldName + "\" — forcing \"Havoc\" for bases and border.");
            worldName = "Havoc";
        } else {
            worldName = w;
        }
        String sw = spawnWorld == null ? "" : spawnWorld.trim();
        if (sw.isEmpty() || "world".equalsIgnoreCase(sw)) {
            plugin.getLogger().warning("Havoc: spawn world was \"" + spawnWorld + "\" — using base world \"" + worldName + "\".");
            spawnWorld = worldName;
        } else {
            spawnWorld = sw;
        }
    }

    private static int clampChunkLocal(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 15) {
            return 15;
        }
        return v;
    }

    private static int[] parsePair(String raw, int defA, int defB) {
        if (raw == null || raw.trim().isEmpty()) {
            return new int[]{defA, defB};
        }
        String[] p = raw.trim().split("\\s*,\\s*");
        if (p.length != 2) {
            return new int[]{defA, defB};
        }
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (NumberFormatException e) {
            return new int[]{defA, defB};
        }
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

    /**
     * When true, paste corner uses {@code origin + clipboard.getOffset()}; when false, {@code origin - offset}.
     */
    public boolean isWorldeditSchematicOffsetAdd() {
        return worldeditSchematicOffsetAdd;
    }

    /**
     * When spawning bases, only the Y component is applied (vertical nudge). X/Z are ignored so horizontal
     * alignment uses {@link #getChunkCenterLocalX()} / {@link #getChunkCenterLocalZ()} only.
     */
    public int[] getPasteExtraWorldDelta() {
        return pasteExtraWorldDelta;
    }

    /** Local block offset (0..15) within the spawn chunk for horizontal anchor of {@code schematic-center-from-min}. */
    public int getChunkCenterLocalX() {
        return chunkCenterLocalX;
    }

    public int getChunkCenterLocalZ() {
        return chunkCenterLocalZ;
    }

    /** If true, paste Y is chosen so the lowest non-air schematic row sits on top of bedrock at the center column. */
    public boolean isVerticalPasteSnapToBedrock() {
        return verticalPasteSnapToBedrock;
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

    public Set<Material> getBreachMaterials() {
        return breachMaterials;
    }

    public int getMaintainerInitialDelayTicks() {
        return maintainerInitialDelayTicks;
    }

    public int getMaintainerPeriodTicks() {
        return maintainerPeriodTicks;
    }

    public int getRestoreTickerPeriodTicks() {
        return restoreTickerPeriodTicks;
    }

    public int getSpawnWorkerPeriodTicks() {
        return spawnWorkerPeriodTicks;
    }

    public int getSpawnMaxAttempts() {
        return spawnMaxAttempts;
    }

    public int getSpawnBorderPaddingBlocks() {
        return spawnBorderPaddingBlocks;
    }

    public int getSpawnSearchAttemptsPerTick() {
        return spawnSearchAttemptsPerTick;
    }

    public int getSpawnPreloadChunksPerTick() {
        return spawnPreloadChunksPerTick;
    }

    public int getSpawnSnapshotColumnsPerTick() {
        return spawnSnapshotColumnsPerTick;
    }

    public int getSpawnPasteColumnsPerTick() {
        return spawnPasteColumnsPerTick;
    }

    public int getSpawnClaimChunksPerTick() {
        return spawnClaimChunksPerTick;
    }

    public int getMinBaseSeparationBlocks() {
        return minBaseSeparationBlocks;
    }

    public int getMinOriginDistanceBlocks() {
        return minOriginDistanceBlocks;
    }

    public long getLockNotifyCooldownMs() {
        return lockNotifyCooldownMs;
    }

    public String getProgressionResetTimezone() {
        return progressionResetTimezone;
    }

    public int getMaxLogLines() {
        return maxLogLines;
    }

    public int getMaxLogDays() {
        return maxLogDays;
    }

    public boolean isArchiveLogsOnRotate() {
        return archiveLogsOnRotate;
    }
}
