package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.lifecycle.ClaimService;
import com.kartersanamo.havoc.base.lifecycle.RestoreEngine;
import com.kartersanamo.havoc.base.lifecycle.RewardService;
import com.kartersanamo.havoc.base.lifecycle.SpawnPlanner;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.faction.FactionsBridge;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BaseService {

    private static final Comparator<ChunkKey> CHUNK_KEY_ORDER = new Comparator<ChunkKey>() {
        @Override
        public int compare(ChunkKey a, ChunkKey b) {
            int c = Integer.compare(a.getX(), b.getX());
            return c != 0 ? c : Integer.compare(a.getZ(), b.getZ());
        }
    };

    private final Havoc plugin;
    private final Random random = new Random();
    private final Map<ChunkKey, UUID> chunkOwners = new ConcurrentHashMap<ChunkKey, UUID>();
    private final Map<UUID, ActiveHavocBase> basesById = new ConcurrentHashMap<UUID, ActiveHavocBase>();
    private Object havocFaction;
    private int restoreTaskId = -1;
    private int maintainerTaskId = -1;
    private int spawnWorkerTaskId = -1;
    private final SpawnPlanner spawnPlanner = new SpawnPlanner();
    private final ClaimService claimService = new ClaimService();
    private final RewardService rewardService = new RewardService();
    private final RestoreEngine restoreEngine = new RestoreEngine();

    public BaseService(Havoc plugin) {
        this.plugin = plugin;
    }

    public void start() {
        FactionsBridge fb = plugin.getFactionsBridge();
        HavocConfig cfg = plugin.getHavocConfig();
        if (!fb.isReady()) {
            return;
        }
        try {
            havocFaction = fb.getHavocFaction(cfg.getHavocFactionTag());
            if (havocFaction == null || fb.isWilderness(havocFaction)) {
                plugin.getLogger().severe("No Havoc faction with tag \"" + cfg.getHavocFactionTag() + "\". Create it in SaberFactions first.");
                HavocDebug.announce(plugin, "Havoc faction missing or wilderness — bases will not spawn.");
            } else {
                HavocDebug.announce(plugin, "Hooked Havoc faction tag \"" + cfg.getHavocFactionTag() + "\".");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Could not resolve Havoc faction: " + e.getMessage());
            HavocDebug.announce(plugin, "Faction resolve error: " + e.getMessage());
        }
        maintainerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                maintainPopulation();
            }
        }, plugin.getHavocConfig().getMaintainerInitialDelayTicks(), plugin.getHavocConfig().getMaintainerPeriodTicks());
        restoreTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tickRestores();
            }
        }, 1L, plugin.getHavocConfig().getRestoreTickerPeriodTicks());
        spawnWorkerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tickSpawnWorker();
            }
        }, 1L, plugin.getHavocConfig().getSpawnWorkerPeriodTicks());
        HavocDebug.announce(plugin, "Base maintainer + restore tickers started.");
    }

    public void shutdown() {
        cancelTimers();
    }

    /**
     * Cancels timers, pending satellite jobs, restores snapshots, unclaims all Havoc base chunks, and clears registry.
     */
    public void shutdownFull() {
        HavocDebug.announce(plugin, "Plugin disable: cleaning up all Havoc bases (" + basesById.size() + ") …");
        cancelTimers();
        for (ActiveHavocBase b : new ArrayList<ActiveHavocBase>(basesById.values())) {
            if (b.satelliteTaskId != -1) {
                Bukkit.getScheduler().cancelTask(b.satelliteTaskId);
                b.satelliteTaskId = -1;
                HavocDebug.announce(plugin, "Cancelled satellite timer for base " + shortId(b.id));
            }
            World w = Bukkit.getWorld(b.worldName);
            if (w != null && b.terrainSnapshot != null) {
                HavocDebug.announce(plugin, "Restoring pre-base terrain for " + shortId(b.id) + " (" + b.difficulty + ") …");
                b.terrainSnapshot.applyAll(w);
            }
            if (w != null) {
                for (ChunkKey key : b.claimedChunks) {
                    if (!key.getWorld().equals(w.getName())) {
                        continue;
                    }
                    try {
                        plugin.getFactionsBridge().unclaimChunk(w.getChunkAt(key.getX(), key.getZ()));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Unclaim on disable: " + e.getMessage());
                    }
                }
            }
            for (ChunkKey key : b.claimedChunks) {
                chunkOwners.remove(key);
            }
        }
        basesById.clear();
        HavocDebug.announce(plugin, "Havoc cleanup finished.");
    }

    private void cancelTimers() {
        if (maintainerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(maintainerTaskId);
            maintainerTaskId = -1;
        }
        if (restoreTaskId != -1) {
            Bukkit.getScheduler().cancelTask(restoreTaskId);
            restoreTaskId = -1;
        }
        if (spawnWorkerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(spawnWorkerTaskId);
            spawnWorkerTaskId = -1;
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private void maintainPopulation() {
        if (havocFaction == null) {
            return;
        }
        for (BaseDifficulty d : BaseDifficulty.values()) {
            int want = plugin.getHavocConfig().basesToSpawn(d);
            int have = countActive(d);
            int queued = spawnPlanner.queuedCount(d);
            for (int i = have + queued; i < want; i++) {
                spawnPlanner.enqueue(d);
            }
        }
    }

    private void tickSpawnWorker() {
        spawnPlanner.tick(new SpawnPlanner.SpawnTaskFactory() {
            @Override
            public SpawnPlanner.SpawnTask create(BaseDifficulty difficulty) {
                return new SpawnPlan(difficulty);
            }
        });
    }

    private int countActive(BaseDifficulty d) {
        int n = 0;
        for (ActiveHavocBase b : basesById.values()) {
            if (b.difficulty == d && b.state == BaseState.ACTIVE) {
                n++;
            }
        }
        return n;
    }

    public ActiveHavocBase findByChunk(Chunk c) {
        UUID id = chunkOwners.get(ChunkKey.of(c));
        if (id == null) {
            return null;
        }
        return basesById.get(id);
    }

    public List<ActiveHavocBase> listAllBasesSorted() {
        List<ActiveHavocBase> list = new ArrayList<ActiveHavocBase>(basesById.values());
        Collections.sort(list, new Comparator<ActiveHavocBase>() {
            @Override
            public int compare(ActiveHavocBase a, ActiveHavocBase b) {
                int w = a.worldName.compareTo(b.worldName);
                if (w != 0) {
                    return w;
                }
                int cx = Integer.compare(a.centerChunkX, b.centerChunkX);
                if (cx != 0) {
                    return cx;
                }
                return Integer.compare(a.centerChunkZ, b.centerChunkZ);
            }
        });
        return list;
    }

    public ActiveHavocBase getBaseById(UUID id) {
        return basesById.get(id);
    }

    public boolean adminForceStartRestore(UUID id) {
        ActiveHavocBase b = basesById.get(id);
        if (b == null || b.state != BaseState.ACTIVE) {
            return false;
        }
        World w = Bukkit.getWorld(b.worldName);
        if (w == null) {
            return false;
        }
        Location breachLoc = new Location(w, b.obsidianCenterX, b.obsidianCenterY, b.obsidianCenterZ);
        breach(b, breachLoc, null);
        return true;
    }

    public boolean tryBreachBlock(Block block, Player progressionCredit) {
        ActiveHavocBase base = findByChunk(block.getChunk());
        if (base == null || base.state != BaseState.ACTIVE) {
            return false;
        }
        if (!base.isCenterChunk(block.getChunk())) {
            return false;
        }
        if (!plugin.getHavocConfig().getBreachMaterials().contains(block.getType())) {
            return false;
        }
        breach(base, block.getLocation(), progressionCredit);
        return true;
    }

    public void tryBreachFromExplosion(List<Block> blocks, Location epicenter) {
        World world = epicenter.getWorld();
        if (world == null) {
            return;
        }
        Player credit = findNearestNonHavoc(world, epicenter, plugin.getHavocConfig().getRewardRadius() * 2.0);
        for (Block b : blocks) {
            if (tryBreachBlock(b, credit)) {
                return;
            }
        }
        tryBreachExplosionEpicenterInCoreChunk(epicenter, credit);
    }

    /**
     * Breach when the explosion epicenter lies in the core chunk (obsidian chunk), even if no
     * {@link HavocConfig#getBreachMaterials()} block appears in {@code EntityExplodeEvent#getBlockList()}.
     */
    private void tryBreachExplosionEpicenterInCoreChunk(Location epicenter, Player progressionCredit) {
        World world = epicenter.getWorld();
        if (world == null) {
            return;
        }
        Chunk ch = world.getChunkAt(epicenter);
        ActiveHavocBase base = findByChunk(ch);
        if (base == null || base.state != BaseState.ACTIVE) {
            return;
        }
        if (!base.isCenterChunk(ch)) {
            return;
        }
        Location breachLoc = new Location(world, epicenter.getBlockX(), epicenter.getBlockY(), epicenter.getBlockZ());
        breach(base, breachLoc, progressionCredit);
    }

    private Player findNearestNonHavoc(World world, Location loc, double maxDistance) {
        if (havocFaction == null) {
            return null;
        }
        double best = maxDistance * maxDistance;
        Player pick = null;
        for (Player p : world.getPlayers()) {
            try {
                Object pf = plugin.getFactionsBridge().getPlayerFaction(p);
                if (pf != null && plugin.getFactionsBridge().factionsEqual(pf, havocFaction)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            double d = p.getLocation().distanceSquared(loc);
            if (d < best) {
                best = d;
                pick = p;
            }
        }
        return pick;
    }

    private synchronized void breach(ActiveHavocBase base, Location breachLoc, Player progressionCredit) {
        if (base.state != BaseState.ACTIVE) {
            return;
        }
        base.state = BaseState.RESTORING;
        World world = Bukkit.getWorld(base.worldName);
        if (world == null) {
            return;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        HavocDebug.announce(plugin, "BREACH " + base.difficulty + " base ~" + shortId(base.id) + " at chunk " + base.centerChunkX + "," + base.centerChunkZ
                + " block " + breachLoc.getBlockX() + "," + breachLoc.getBlockY() + "," + breachLoc.getBlockZ());
        plugin.getLogService().log("BASE_BREACH",
                progressionCredit == null ? "" : progressionCredit.getName(),
                shortId(base.id),
                breachLoc,
                "difficulty=" + base.difficulty + ", state=" + base.state);
        long now = System.currentTimeMillis();
        base.raidEndMs = now + cfg.getRestoreSeconds() * 1000L;
        base.satellite = SatelliteRing.capture(world, base.centerChunkX, base.centerChunkZ, cfg.getWatchChunkRadius());
        base.restoreCursor = 0;
        HavocDebug.announce(plugin, "Satellite ring snapshot saved (watch radius " + cfg.getWatchChunkRadius() + " chunks).");

        Location spawn = cfg.getSpawnLocation();
        int tp = 0;
        for (Player p : world.getPlayers()) {
            if (!base.containsBlockColumn(p.getLocation().getBlockX(), p.getLocation().getBlockZ())) {
                continue;
            }
            try {
                Object at = plugin.getFactionsBridge().getFactionAtLocation(p.getLocation());
                if (at != null && plugin.getFactionsBridge().factionsEqual(at, havocFaction)) {
                    p.teleport(spawn);
                    tp++;
                }
            } catch (Exception ignored) {
            }
        }
        if (tp > 0) {
            HavocDebug.announce(plugin, "Teleported " + tp + " player(s) inside Havoc claim to spawn.");
        }

        rewardService.processBreachRewards(plugin, havocFaction, base, breachLoc, progressionCredit,
                new RewardService.NextBasePicker() {
                    @Override
                    public ActiveHavocBase pick(BaseDifficulty difficulty, UUID exclude) {
                        return pickRandomActive(difficulty, exclude);
                    }
                });

        long delay = cfg.getRestoreSeconds() * 20L;
        final ActiveHavocBase ref = base;
        base.satelliteTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                ref.satelliteTaskId = -1;
                if (ref.satellite != null) {
                    try {
                        HavocDebug.announce(plugin, "Satellite reset firing for base ~" + shortId(ref.id));
                        ref.satellite.restoreAndUnclaimAll(world, plugin.getFactionsBridge());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Satellite reset failed: " + e.getMessage());
                        HavocDebug.announce(plugin, "Satellite reset FAILED: " + e.getMessage());
                    }
                }
            }
        }, delay);
        HavocDebug.announce(plugin, "Terrain restore started (" + cfg.getRestoreSeconds() + "s); satellite reset scheduled same delay.");
    }

    private ActiveHavocBase pickRandomActive(BaseDifficulty d, UUID exclude) {
        List<ActiveHavocBase> list = new ArrayList<ActiveHavocBase>();
        for (ActiveHavocBase b : basesById.values()) {
            if (b.state == BaseState.ACTIVE && b.difficulty == d && !b.id.equals(exclude)) {
                list.add(b);
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }

    private void tickRestores() {
        restoreEngine.tick(plugin, basesById, chunkOwners);
    }

    public boolean isRestoringFootprint(Location loc) {
        ActiveHavocBase b = findByChunk(loc.getChunk());
        return b != null && b.state == BaseState.RESTORING && b.containsBlockColumn(loc.getBlockX(), loc.getBlockZ());
    }

    public boolean shouldCancelBlockChange(Location loc) {
        return isRestoringFootprint(loc) || isSatelliteLocked(loc);
    }

    public boolean isSatelliteLocked(Location loc) {
        for (ActiveHavocBase b : basesById.values()) {
            if (b.state == BaseState.RESTORING && b.satellite != null && System.currentTimeMillis() < b.raidEndMs) {
                if (b.satellite.isInWatch(loc)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean shouldDenyEnter(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        for (ActiveHavocBase b : basesById.values()) {
            if (b.state != BaseState.RESTORING) {
                continue;
            }
            boolean toIn = b.containsBlockColumn(to.getBlockX(), to.getBlockZ());
            boolean fromIn = b.containsBlockColumn(from.getBlockX(), from.getBlockZ());
            if (toIn && !fromIn) {
                return true;
            }
        }
        return false;
    }

    public boolean trySpawnOne(BaseDifficulty d) {
        if (havocFaction == null) {
            HavocDebug.announce(plugin, "trySpawnOne(" + d + "): no Havoc faction.");
            return false;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Configured world not loaded: " + cfg.getWorldName());
            return false;
        }
        File schem = new File(plugin.getDataFolder(), cfg.getSchematicsFolder() + "/" + cfg.schematicFileName(d));
        if (!schem.isFile()) {
            HavocDebug.announce(plugin, "Missing schematic file: " + schem.getAbsolutePath());
            return false;
        }
        SchematicService paster = new SchematicService();
        CuboidClipboard clip;
        try {
            clip = paster.loadClipboard(schem);
        } catch (IOException | DataException e) {
            HavocDebug.announce(plugin, "Schematic load failed: " + e.getMessage());
            return false;
        }
        SchematicAnalysis analysis = SchematicAnalysis.analyze(clip);
        int w = analysis.width;
        int h = analysis.height;
        int len = analysis.length;
        HavocDebug.announce(plugin, "Analyzed " + d + " schematic size " + w + " x " + h + " x " + len
                + " (non-air Y " + analysis.lowestNonAirY + ".." + analysis.highestNonAirY + ").");

        int half = cfg.getBorderHalfSize();
        int pad = cfg.getSpawnBorderPaddingBlocks();
        int minC = (-half + pad) / 16;
        int maxC = (half - pad) / 16;
        int sep = cfg.getMinCenterSeparationChunks();
        int[] off = cfg.getSchematicCenterOffset(d);
        int[] ex = cfg.getPasteExtraWorldDelta();
        int chunkLocalX = cfg.getChunkCenterLocalX();
        int chunkLocalZ = cfg.getChunkCenterLocalZ();
        for (int attempt = 0; attempt < cfg.getSpawnMaxAttempts(); attempt++) {
            int chunkCx = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            int chunkCz = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            int chunkMidX = chunkCx * 16 + chunkLocalX;
            int chunkMidZ = chunkCz * 16 + chunkLocalZ;
            int targetY = cfg.getPasteCenterWorldY();
            int ox = chunkMidX - off[0];
            int oz = chunkMidZ - off[2];
            int oy;
            if (cfg.isVerticalPasteSnapToBedrock()) {
                int roof = SchematicPlacement.highestBedrockY(world, chunkMidX, chunkMidZ);
                if (roof >= 0) {
                    oy = roof + 1 - analysis.lowestNonAirY + ex[1];
                    HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": SNAP above bedrock roof " + roof
                            + ", lowest schematic non-air Y=" + analysis.lowestNonAirY + " → originY=" + oy);
                } else {
                    oy = targetY - off[1] + ex[1];
                    HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": no bedrock at " + chunkMidX + "," + chunkMidZ + " — using config Y.");
                }
            } else {
                oy = targetY - off[1] + ex[1];
            }
            int worldH = world.getMaxHeight();
            int yMax = worldH - h;
            if (oy < 0) {
                HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": clamp paste Y " + oy + " → 0 (under world).");
                oy = 0;
            }
            if (oy > yMax) {
                HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": clamp paste Y " + oy + " → " + yMax + " (over height).");
                oy = yMax;
            }
            int obsX = ox + off[0];
            int obsY = oy + off[1];
            int obsZ = oz + off[2];
            int occCx = Math.floorDiv(obsX, 16);
            int occCz = Math.floorDiv(obsZ, 16);
            if (!fitsInBorder(ox, oz, w, len, half)) {
                continue;
            }
            if (!isFarEnoughFromOrigin(obsX, obsZ)) {
                continue;
            }
            if (!isFarEnoughFromOtherBases(obsX, obsZ, world.getName())) {
                continue;
            }
            HashSet<ChunkKey> claimSet = new HashSet<ChunkKey>();
            analysis.collectClaimChunks(world.getName(), ox, oz, claimSet);
            analysis.ensureAnchorChunkClaimed(world.getName(), ox, oz, off[0], off[2], claimSet);
            if (overlapsExistingFootprint(world.getName(), ox, oz, w, len)) {
                continue;
            }
            if (overlapsExistingClaims(claimSet)) {
                continue;
            }
            int rNew = footprintChunkRadiusFromClaims(occCx, occCz, claimSet);
            if (!farEnough(occCx, occCz, sep, rNew)) {
                continue;
            }
            if (spawnAt(world, d, clip, ox, oy, oz, off, w, h, len, claimSet, rNew)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fitsInBorder(int originX, int originZ, int w, int len, int half) {
        return originX >= -half && originX + w - 1 <= half && originZ >= -half && originZ + len - 1 <= half;
    }

    private static int footprintChunkRadiusFromClaims(int obsidianChunkX, int obsidianChunkZ, Set<ChunkKey> claimChunks) {
        int r = 0;
        for (ChunkKey k : claimChunks) {
            r = Math.max(r, Math.max(Math.abs(k.getX() - obsidianChunkX), Math.abs(k.getZ() - obsidianChunkZ)));
        }
        return r;
    }

    private boolean overlapsExistingFootprint(String worldName, int ox, int oz, int w, int len) {
        return claimService.overlapsExistingFootprint(basesById, worldName, ox, oz, w, len);
    }

    private boolean overlapsExistingClaims(Set<ChunkKey> claimSet) {
        return claimService.overlapsExistingClaims(chunkOwners, claimSet);
    }

    private static long distSq2D(int ax, int az, int bx, int bz) {
        long dx = (long) ax - bx;
        long dz = (long) az - bz;
        return dx * dx + dz * dz;
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

    private boolean farEnough(int cx, int cz, int sep, int newRadiusChunks) {
        for (ActiveHavocBase b : basesById.values()) {
            int d = Math.max(Math.abs(b.centerChunkX - cx), Math.abs(b.centerChunkZ - cz));
            int need = sep + newRadiusChunks + b.chunkFootprintRadius;
            if (d < need) {
                return false;
            }
        }
        return true;
    }

    private boolean spawnAt(World world, BaseDifficulty d, CuboidClipboard clip,
            int ox, int oy, int oz, int[] schematicCenterFromMin, int w, int h, int len,
            Set<ChunkKey> claimChunks, int chunkFootprintRadius) {
        ActiveHavocBase base = new ActiveHavocBase(d, world.getName());
        base.pasteOriginX = ox;
        base.pasteOriginY = oy;
        base.pasteOriginZ = oz;
        base.footprintSizeX = w;
        base.footprintSizeZ = len;
        base.chunkFootprintRadius = chunkFootprintRadius;
        base.obsidianCenterX = ox + schematicCenterFromMin[0];
        base.obsidianCenterY = oy + schematicCenterFromMin[1];
        base.obsidianCenterZ = oz + schematicCenterFromMin[2];
        base.centerChunkX = Math.floorDiv(base.obsidianCenterX, 16);
        base.centerChunkZ = Math.floorDiv(base.obsidianCenterZ, 16);

        int minCx = Math.floorDiv(ox, 16);
        int maxCx = Math.floorDiv(ox + w - 1, 16);
        int minCz = Math.floorDiv(oz, 16);
        int maxCz = Math.floorDiv(oz + len - 1, 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load();
                }
            }
        }
        ArrayList<ChunkKey> sortedClaims = new ArrayList<ChunkKey>(claimChunks);
        Collections.sort(sortedClaims, CHUNK_KEY_ORDER);
        for (ChunkKey key : sortedClaims) {
            if (!key.getWorld().equals(world.getName())) {
                continue;
            }
            Chunk ch = world.getChunkAt(key.getX(), key.getZ());
            if (!ch.isLoaded()) {
                ch.load();
            }
        }

        HavocDebug.announce(plugin, "Preparing snapshot " + w + "x" + len + " columns full height @ origin " + ox + "," + oz + " …");

        int worldH = world.getMaxHeight();
        ColumnBoxSnapshot snap = ColumnBoxSnapshot.capture(world, ox, oz, w, len, worldH);
        base.terrainSnapshot = snap;

        try {
            HavocDebug.announce(plugin, "Placing " + d + " block-by-block at origin " + ox + "," + oy + "," + oz
                    + " obsidian " + base.obsidianCenterX + "," + base.obsidianCenterY + "," + base.obsidianCenterZ
                    + " (clipboard min = chunk alignment; claims=" + sortedClaims.size() + " chunks).");
            SchematicBlockPlacer.pasteAt(world, clip, ox, oy, oz);
        } catch (IOException | MaxChangedBlocksException e) {
            plugin.getLogger().severe("Schematic paste failed: " + e.getMessage());
            HavocDebug.announce(plugin, "PASTE FAILED: " + e.getMessage());
            snap.applyAll(world);
            return false;
        }
        try {
            for (ChunkKey key : sortedClaims) {
                if (!key.getWorld().equals(world.getName())) {
                    continue;
                }
                Chunk ch = world.getChunkAt(key.getX(), key.getZ());
                plugin.getFactionsBridge().claimChunkForFaction(ch, havocFaction);
                base.claimedChunks.add(key);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Claim failed, reverting terrain: " + e.getMessage());
            HavocDebug.announce(plugin, "CLAIM FAILED, reverting: " + e.getMessage());
            snap.applyAll(world);
            base.claimedChunks.clear();
            return false;
        }
        basesById.put(base.id, base);
        for (ChunkKey key : base.claimedChunks) {
            chunkOwners.put(key, base.id);
        }
        HavocDebug.announce(plugin, "Spawned " + d + " base ~" + shortId(base.id) + " envelope chunks " + minCx + "," + minCz + " → " + maxCx + "," + maxCz
                + " (obsidian center chunk " + base.centerChunkX + "," + base.centerChunkZ + ", faction claims=" + base.claimedChunks.size() + ").");
        plugin.getLogger().info("Spawned " + d + " Havoc base ~" + shortId(base.id) + " at chunk " + base.centerChunkX + "," + base.centerChunkZ);
        plugin.getLogService().log("BASE_SPAWN", "", shortId(base.id),
                new Location(world, base.obsidianCenterX, base.obsidianCenterY, base.obsidianCenterZ),
                "difficulty=" + d + ", claims=" + base.claimedChunks.size());
        return true;
    }

    private final class SpawnPlan implements SpawnPlanner.SpawnTask {
        private static final int PHASE_SEARCH = 0;
        private static final int PHASE_PRELOAD = 1;
        private static final int PHASE_SNAPSHOT = 2;
        private static final int PHASE_PASTE = 3;
        private static final int PHASE_CLAIM = 4;
        private static final int PHASE_FINALIZE = 5;

        private int preloadChunksPerTick;
        private int snapshotColumnsPerTick;
        private int pasteColumnsPerTick;
        private int claimChunksPerTick;
        private int searchAttemptsPerTick;
        private int maxAttempts;

        private final BaseDifficulty difficulty;
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

        private SpawnPlan(BaseDifficulty difficulty) {
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
                    plugin.getLogService().log("SPAWN_FAIL", "", "", null,
                            "difficulty=" + difficulty + ", attempts=" + maxAttempts);
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
            if (overlapsExistingFootprint(world.getName(), ox, oz, w, len)) {
                return false;
            }
            if (overlapsExistingClaims(claimSet)) {
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
            plugin.getLogService().log("SPAWN_FAIL", "", "", new Location(world, ox, oy, oz),
                    "difficulty=" + difficulty + ", reason=" + debugMsg);
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
    }

}
