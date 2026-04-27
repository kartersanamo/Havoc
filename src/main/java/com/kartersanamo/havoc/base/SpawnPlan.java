package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.lifecycle.ClaimService;
import com.kartersanamo.havoc.base.lifecycle.SpawnPlanner;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.world.ColumnBoxSnapshot;
import com.kartersanamo.havoc.world.SchematicAnalysis;
import com.kartersanamo.havoc.world.SchematicBlockPlacer;
import com.kartersanamo.havoc.world.SchematicPlacement;
import com.kartersanamo.havoc.world.SchematicService;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.data.DataException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class SpawnPlan implements SpawnPlanner.SpawnTask {
    private static final int PHASE_SEARCH = 0;
    private static final int PHASE_PRELOAD = 1;
    private static final int PHASE_SNAPSHOT = 2;
    private static final int PHASE_PASTE = 3;
    private static final int PHASE_CLAIM = 4;
    private static final int PHASE_FINALIZE = 5;

    private static final Comparator<ChunkKey> CHUNK_KEY_ORDER = new Comparator<ChunkKey>() {
        @Override
        public int compare(ChunkKey a, ChunkKey b) {
            int c = Integer.compare(a.getX(), b.getX());
            return c != 0 ? c : Integer.compare(a.getZ(), b.getZ());
        }
    };

    private final Havoc plugin;
    private final Object havocFaction;
    private final Map<UUID, ActiveHavocBase> basesById;
    private final Map<ChunkKey, UUID> chunkOwners;
    private final ClaimService claimService;
    private final BaseDifficulty difficulty;
    private int preloadChunksPerTick;
    private int snapshotColumnsPerTick;
    private int pasteColumnsPerTick;
    private int claimChunksPerTick;
    private int searchAttemptsPerTick;
    private int maxAttempts;

    private boolean initialized;
    private int attempt;
    private World world;
    private CuboidClipboard clip;
    private SchematicAnalysis analysis;
    private int w;
    private int h;
    private int len;
    private int half;
    private int minC;
    private int maxC;
    private int sep;
    private int[] off;
    private int[] ex;
    private int chunkLocalX;
    private int chunkLocalZ;

    private int phase = PHASE_SEARCH;
    private int ox;
    private int oy;
    private int oz;
    private int chunkFootprintRadius;
    private ActiveHavocBase base;
    private ColumnBoxSnapshot snapshot;
    private ArrayList<ChunkKey> sortedClaims;
    private ArrayList<ChunkKey> preloadChunks;
    private int preloadCursor;
    private int snapshotColumnCursor;
    private EditSession pasteSession;
    private int pasteColumnCursor;
    private int claimCursor;

    SpawnPlan(Havoc plugin, Object havocFaction, Map<UUID, ActiveHavocBase> basesById,
              Map<ChunkKey, UUID> chunkOwners, ClaimService claimService, BaseDifficulty difficulty) {
        this.plugin = plugin;
        this.havocFaction = havocFaction;
        this.basesById = basesById;
        this.chunkOwners = chunkOwners;
        this.claimService = claimService;
        this.difficulty = difficulty;
    }

    @Override
    public BaseDifficulty difficulty() {
        return difficulty;
    }

    @Override
    public boolean tick() {
        if (!initialized) {
            initialized = true;
            if (!init()) {
                return true;
            }
        }
        switch (phase) {
            case PHASE_SEARCH:
                return tickSearch();
            case PHASE_PRELOAD:
                return tickPreload();
            case PHASE_SNAPSHOT:
                return tickSnapshot();
            case PHASE_PASTE:
                return tickPaste();
            case PHASE_CLAIM:
                return tickClaim();
            case PHASE_FINALIZE:
                return finalizeSpawn();
            default:
                return true;
        }
    }

    private boolean init() {
        if (havocFaction == null) {
            return false;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Configured world not loaded: " + cfg.getWorldName());
            return false;
        }
        File schem = new File(plugin.getDataFolder(), cfg.getSchematicsFolder() + "/" + cfg.schematicFileName(difficulty));
        if (!schem.isFile()) {
            HavocDebug.announce(plugin, "Missing schematic file: " + schem.getAbsolutePath());
            return false;
        }
        SchematicService paster = new SchematicService();
        try {
            clip = paster.loadClipboard(schem);
        } catch (IOException | DataException e) {
            HavocDebug.announce(plugin, "Schematic load failed: " + e.getMessage());
            return false;
        }
        analysis = SchematicAnalysis.analyze(clip);
        w = analysis.width;
        h = analysis.height;
        len = analysis.length;
        half = cfg.getBorderHalfSize();
        int pad = cfg.getSpawnBorderPaddingBlocks();
        minC = (-half + pad) / 16;
        maxC = (half - pad) / 16;
        sep = cfg.getMinCenterSeparationChunks();
        off = cfg.getSchematicCenterOffset(difficulty);
        ex = cfg.getPasteExtraWorldDelta();
        chunkLocalX = cfg.getChunkCenterLocalX();
        chunkLocalZ = cfg.getChunkCenterLocalZ();
        preloadChunksPerTick = cfg.getSpawnPreloadChunksPerTick();
        snapshotColumnsPerTick = cfg.getSpawnSnapshotColumnsPerTick();
        pasteColumnsPerTick = cfg.getSpawnPasteColumnsPerTick();
        claimChunksPerTick = cfg.getSpawnClaimChunksPerTick();
        searchAttemptsPerTick = cfg.getSpawnSearchAttemptsPerTick();
        maxAttempts = cfg.getSpawnMaxAttempts();
        HavocDebug.announce(plugin, "Spawn worker " + difficulty + ": schematic " + w + " x " + h + " x " + len + " prepared.");
        return true;
    }

    private boolean tickSearch() {
        for (int i = 0; i < searchAttemptsPerTick; i++) {
            if (attempt >= maxAttempts) {
                HavocDebug.announce(plugin, "Population: could not spawn " + difficulty + " after " + maxAttempts + " attempts.");
                plugin.getLogService().log("SPAWN_FAIL", "", "", null, "difficulty=" + difficulty + ", attempts=" + maxAttempts);
                return true;
            }
            if (selectCandidate()) {
                return false;
            }
            attempt++;
        }
        return false;
    }

    private boolean selectCandidate() {
        HavocConfig cfg = plugin.getHavocConfig();
        int chunkCx = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
        int chunkCz = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
        int chunkMidX = chunkCx * 16 + chunkLocalX;
        int chunkMidZ = chunkCz * 16 + chunkLocalZ;
        int targetY = cfg.getPasteCenterWorldY();
        ox = chunkMidX - off[0];
        oz = chunkMidZ - off[2];
        if (cfg.isVerticalPasteSnapToBedrock()) {
            int roof = SchematicPlacement.highestBedrockY(world, chunkMidX, chunkMidZ);
            if (roof >= 0) {
                oy = roof + 1 - analysis.lowestNonAirY + ex[1];
            } else {
                oy = targetY - off[1] + ex[1];
            }
        } else {
            oy = targetY - off[1] + ex[1];
        }
        int yMax = world.getMaxHeight() - h;
        if (oy < 0) {
            oy = 0;
        }
        if (oy > yMax) {
            oy = yMax;
        }
        int obsX = ox + off[0];
        int obsZ = oz + off[2];
        int occCx = Math.floorDiv(obsX, 16);
        int occCz = Math.floorDiv(obsZ, 16);
        if (!fitsInBorder(ox, oz, w, len, half)) {
            return false;
        }
        if (!isFarEnoughFromOrigin(obsX, obsZ)) {
            return false;
        }
        if (!isFarEnoughFromOtherBases(obsX, obsZ, world.getName())) {
            return false;
        }
        HashSet<ChunkKey> claimSet = new HashSet<ChunkKey>();
        analysis.collectClaimChunks(world.getName(), ox, oz, claimSet);
        analysis.ensureAnchorChunkClaimed(world.getName(), ox, oz, off[0], off[2], claimSet);
        if (claimService.overlapsExistingFootprint(basesById, world.getName(), ox, oz, w, len)) {
            return false;
        }
        if (claimService.overlapsExistingClaims(chunkOwners, claimSet)) {
            return false;
        }
        chunkFootprintRadius = footprintChunkRadiusFromClaims(occCx, occCz, claimSet);
        if (!farEnough(occCx, occCz, sep, chunkFootprintRadius)) {
            return false;
        }
        base = new ActiveHavocBase(difficulty, world.getName());
        base.pasteOriginX = ox;
        base.pasteOriginY = oy;
        base.pasteOriginZ = oz;
        base.footprintSizeX = w;
        base.footprintSizeZ = len;
        base.chunkFootprintRadius = chunkFootprintRadius;
        base.obsidianCenterX = ox + off[0];
        base.obsidianCenterY = oy + off[1];
        base.obsidianCenterZ = oz + off[2];
        base.centerChunkX = Math.floorDiv(base.obsidianCenterX, 16);
        base.centerChunkZ = Math.floorDiv(base.obsidianCenterZ, 16);

        sortedClaims = new ArrayList<ChunkKey>(claimSet);
        Collections.sort(sortedClaims, CHUNK_KEY_ORDER);
        preloadChunks = new ArrayList<ChunkKey>();
        HashSet<ChunkKey> preloadSet = new HashSet<ChunkKey>();
        int minCx = Math.floorDiv(ox, 16);
        int maxCx = Math.floorDiv(ox + w - 1, 16);
        int minCz = Math.floorDiv(oz, 16);
        int maxCz = Math.floorDiv(oz + len - 1, 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkKey key = new ChunkKey(world.getName(), cx, cz);
                if (preloadSet.add(key)) {
                    preloadChunks.add(key);
                }
            }
        }
        for (ChunkKey key : sortedClaims) {
            if (key.getWorld().equals(world.getName()) && preloadSet.add(key)) {
                preloadChunks.add(key);
            }
        }
        preloadCursor = 0;
        snapshot = new ColumnBoxSnapshot(ox, oz, w, len, world.getMaxHeight());
        base.terrainSnapshot = snapshot;
        snapshotColumnCursor = 0;
        pasteSession = null;
        pasteColumnCursor = 0;
        claimCursor = 0;
        phase = PHASE_PRELOAD;
        return true;
    }

    private boolean tickPreload() {
        int done = 0;
        while (preloadCursor < preloadChunks.size() && done < preloadChunksPerTick) {
            ChunkKey key = preloadChunks.get(preloadCursor++);
            Chunk ch = world.getChunkAt(key.getX(), key.getZ());
            if (!ch.isLoaded()) {
                ch.load();
            }
            done++;
        }
        if (preloadCursor >= preloadChunks.size()) {
            HavocDebug.announce(plugin, "Preparing snapshot " + w + "x" + len + " columns full height @ origin " + ox + "," + oz + " …");
            phase = PHASE_SNAPSHOT;
        }
        return false;
    }

    private boolean tickSnapshot() {
        int totalCols = w * len;
        int count = Math.min(snapshotColumnsPerTick, totalCols - snapshotColumnCursor);
        snapshot.captureColumns(world, snapshotColumnCursor, count);
        snapshotColumnCursor += count;
        if (snapshotColumnCursor >= totalCols) {
            phase = PHASE_PASTE;
        }
        return false;
    }

    private boolean tickPaste() {
        if (pasteSession == null) {
            try {
                HavocDebug.announce(plugin, "Placing " + difficulty + " block-by-block at origin " + ox + "," + oy + "," + oz
                        + " obsidian " + base.obsidianCenterX + "," + base.obsidianCenterY + "," + base.obsidianCenterZ
                        + " (clipboard min = chunk alignment; claims=" + sortedClaims.size() + " chunks).");
                pasteSession = SchematicBlockPlacer.createSession(world);
            } catch (IOException e) {
                failSpawn("PASTE FAILED: " + e.getMessage());
                return true;
            }
        }
        int totalCols = w * len;
        int count = Math.min(pasteColumnsPerTick, totalCols - pasteColumnCursor);
        try {
            SchematicBlockPlacer.pasteColumns(pasteSession, clip, ox, oy, oz, pasteColumnCursor, count);
        } catch (MaxChangedBlocksException e) {
            failSpawn("PASTE FAILED: " + e.getMessage());
            return true;
        }
        pasteColumnCursor += count;
        if (pasteColumnCursor >= totalCols) {
            pasteSession.flushQueue();
            pasteSession = null;
            phase = PHASE_CLAIM;
        }
        return false;
    }

    private boolean tickClaim() {
        int done = 0;
        while (claimCursor < sortedClaims.size() && done < claimChunksPerTick) {
            ChunkKey key = sortedClaims.get(claimCursor++);
            if (!key.getWorld().equals(world.getName())) {
                continue;
            }
            try {
                Chunk ch = world.getChunkAt(key.getX(), key.getZ());
                plugin.getFactionsBridge().claimChunkForFaction(ch, havocFaction);
                base.claimedChunks.add(key);
            } catch (Exception e) {
                failSpawn("CLAIM FAILED, reverting: " + e.getMessage());
                return true;
            }
            done++;
        }
        if (claimCursor >= sortedClaims.size()) {
            phase = PHASE_FINALIZE;
        }
        return false;
    }

    private boolean finalizeSpawn() {
        basesById.put(base.id, base);
        for (ChunkKey key : base.claimedChunks) {
            chunkOwners.put(key, base.id);
        }
        int minCx = Math.floorDiv(ox, 16);
        int maxCx = Math.floorDiv(ox + w - 1, 16);
        int minCz = Math.floorDiv(oz, 16);
        int maxCz = Math.floorDiv(oz + len - 1, 16);
        HavocDebug.announce(plugin, "Spawned " + difficulty + " base ~" + shortId(base.id) + " envelope chunks " + minCx + "," + minCz + " → " + maxCx + "," + maxCz
                + " (obsidian center chunk " + base.centerChunkX + "," + base.centerChunkZ + ", faction claims=" + base.claimedChunks.size() + ").");
        plugin.getLogger().info("Spawned " + difficulty + " Havoc base ~" + shortId(base.id) + " at chunk " + base.centerChunkX + "," + base.centerChunkZ);
        plugin.getLogService().log("BASE_SPAWN", "", shortId(base.id),
                new Location(world, base.obsidianCenterX, base.obsidianCenterY, base.obsidianCenterZ),
                "difficulty=" + difficulty + ", claims=" + base.claimedChunks.size());
        return true;
    }

    private void failSpawn(String debugMsg) {
        plugin.getLogger().severe(debugMsg.replace("FAILED: ", "").replace("reverting: ", ""));
        HavocDebug.announce(plugin, debugMsg);
        plugin.getLogService().log("SPAWN_FAIL", "", "", new Location(world, ox, oy, oz), "difficulty=" + difficulty + ", reason=" + debugMsg);
        if (pasteSession != null) {
            pasteSession.flushQueue();
            pasteSession = null;
        }
        if (base != null && !base.claimedChunks.isEmpty()) {
            for (ChunkKey key : new ArrayList<ChunkKey>(base.claimedChunks)) {
                if (!key.getWorld().equals(world.getName())) {
                    continue;
                }
                try {
                    plugin.getFactionsBridge().unclaimChunk(world.getChunkAt(key.getX(), key.getZ()));
                } catch (Exception ignored) {
                }
            }
        }
        if (snapshot != null) {
            snapshot.applyAll(world);
        }
        if (base != null) {
            base.claimedChunks.clear();
        }
    }

    private boolean farEnough(int cx, int cz, int separationChunks, int newRadiusChunks) {
        for (ActiveHavocBase b : basesById.values()) {
            int d = Math.max(Math.abs(b.centerChunkX - cx), Math.abs(b.centerChunkZ - cz));
            int need = separationChunks + newRadiusChunks + b.chunkFootprintRadius;
            if (d < need) {
                return false;
            }
        }
        return true;
    }

    private boolean isFarEnoughFromOrigin(int obsidianX, int obsidianZ) {
        int min = plugin.getHavocConfig().getMinOriginDistanceBlocks();
        long minSq = (long) min * min;
        return distSq2D(obsidianX, obsidianZ, 0, 0) >= minSq;
    }

    private boolean isFarEnoughFromOtherBases(int obsidianX, int obsidianZ, String worldName) {
        int min = plugin.getHavocConfig().getMinBaseSeparationBlocks();
        long minSq = (long) min * min;
        for (ActiveHavocBase b : basesById.values()) {
            if (!b.worldName.equals(worldName)) {
                continue;
            }
            if (distSq2D(obsidianX, obsidianZ, b.obsidianCenterX, b.obsidianCenterZ) < minSq) {
                return false;
            }
        }
        return true;
    }

    private static long distSq2D(int ax, int az, int bx, int bz) {
        long dx = (long) ax - bx;
        long dz = (long) az - bz;
        return dx * dx + dz * dz;
    }

    private static boolean fitsInBorder(int originX, int originZ, int width, int length, int half) {
        return originX >= -half && originX + width - 1 <= half && originZ >= -half && originZ + length - 1 <= half;
    }

    private static int footprintChunkRadiusFromClaims(int obsidianChunkX, int obsidianChunkZ, Set<ChunkKey> claimChunks) {
        int r = 0;
        for (ChunkKey k : claimChunks) {
            r = Math.max(r, Math.max(Math.abs(k.getX() - obsidianChunkX), Math.abs(k.getZ() - obsidianChunkZ)));
        }
        return r;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
