package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.lifecycle.ClaimService;
import com.kartersanamo.havoc.base.lifecycle.RestoreEngine;
import com.kartersanamo.havoc.base.lifecycle.SpawnPlanner;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.event.BaseBreachedEvent;
import com.kartersanamo.havoc.event.BaseSpawnedEvent;
import com.kartersanamo.havoc.event.InternalEventBus;
import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.world.ColumnBoxSnapshot;
import com.kartersanamo.havoc.world.InnerBreachRegion;
import com.kartersanamo.havoc.world.SchematicAnalysis;
import com.kartersanamo.havoc.world.SchematicBlockPlacer;
import com.kartersanamo.havoc.world.SchematicPlacement;
import com.kartersanamo.havoc.world.SchematicService;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.data.DataException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private final RestoreEngine restoreEngine = new RestoreEngine();
    private final InternalEventBus eventBus;
    private long spawnWorkerTicksMeasured;
    private double spawnWorkerAvgMs;

    public BaseService(Havoc plugin, InternalEventBus eventBus) {
        this.plugin = plugin;
        this.eventBus = eventBus;
    }

    public void start() {
        FactionsBridge fb = plugin.getFactionsBridge();
        HavocConfig cfg = plugin.getHavocConfig();
        if (!fb.isReady()) {
            return;
        }
        try {
            havocFaction = fb.ensureHavocFaction(cfg.getHavocFactionTag());
            if (havocFaction == null || fb.isWilderness(havocFaction)) {
                plugin.getLogger().severe("Could not resolve or create Havoc faction with tag \"" + cfg.getHavocFactionTag() + "\".");
                HavocDebug.announce(plugin, "Havoc faction missing or wilderness — bases will not spawn.");
            } else {
                HavocDebug.announce(plugin, "Using Havoc faction tag \"" + cfg.getHavocFactionTag() + "\".");
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

    // Just pre-ensures our queue has the correct number of bases to spawn
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
        long startNs = System.nanoTime();
        spawnPlanner.tick(new SpawnPlanner.SpawnTaskFactory() {
            @Override
            public SpawnPlanner.SpawnTask create(BaseDifficulty difficulty) {
                return new SpawnPlan(plugin, havocFaction, basesById, chunkOwners, claimService, eventBus, difficulty);
            }
        });
        long elapsedNs = System.nanoTime() - startNs;
        recordSpawnWorkerSample(elapsedNs / 1_000_000.0D);
    }

    private void recordSpawnWorkerSample(double elapsedMs) {
        spawnWorkerTicksMeasured++;
        if (spawnWorkerTicksMeasured == 1L) {
            spawnWorkerAvgMs = elapsedMs;
            return;
        }
        spawnWorkerAvgMs += (elapsedMs - spawnWorkerAvgMs) / (double) spawnWorkerTicksMeasured;
    }

    public int getSpawnQueueSize() {
        return spawnPlanner.queuedTotal();
    }

    public String getSpawnActivePhase() {
        return spawnPlanner.activePhaseName();
    }

    public String getSpawnActiveDifficulty() {
        return spawnPlanner.activeDifficultyName();
    }

    public double getSpawnWorkerAvgMs() {
        return spawnWorkerAvgMs;
    }

    public long getSpawnWorkerSamples() {
        return spawnWorkerTicksMeasured;
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

    public boolean isInnerBreachLocation(Location location) {
        if (location == null) {
            return false;
        }
        ActiveHavocBase base = findByChunk(location.getChunk());
        if (base == null || base.state != BaseState.ACTIVE) {
            return false;
        }
        return isInnerBreachLocation(base, location);
    }

    public boolean tryBreachBlock(Block block, Player progressionCredit) {
        if (block == null || block.getType() != Material.OBSIDIAN) {
            return false;
        }
        return tryBreachBrokenInnerObsidian(block.getLocation(), progressionCredit);
    }

    public boolean tryBreachBrokenInnerObsidian(Location location, Player progressionCredit) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        if (block.getType() != Material.AIR) {
            return false;
        }
        ActiveHavocBase base = findByChunk(block.getChunk());
        if (base == null || base.state != BaseState.ACTIVE) {
            return false;
        }
        if (!isInnerBreachLocation(base, location)) {
            return false;
        }
        breach(base, location, resolveBreachCredit(base, location, progressionCredit));
        return true;
    }

    private boolean isInnerBreachLocation(ActiveHavocBase base, Location location) {
        InnerBreachRegion region = base.innerBreachRegion;
        if (region == null || region.isEmpty()) {
            return false;
        }
        return region.containsWorldBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                base.pasteOriginX, base.pasteOriginY, base.pasteOriginZ);
    }

    public void tryBreachFromExplosion(List<Block> blocks, Player progressionCredit) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        List<Location> innerObsidianBreaks = new ArrayList<Location>();
        for (Block block : blocks) {
            if (block == null || block.getType() != Material.OBSIDIAN) {
                continue;
            }
            if (!isInnerBreachLocation(block.getLocation())) {
                continue;
            }
            innerObsidianBreaks.add(block.getLocation().clone());
        }
        if (innerObsidianBreaks.isEmpty()) {
            return;
        }
        final Player credit = progressionCredit;
        final List<Location> pending = innerObsidianBreaks;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Location location : pending) {
                    if (tryBreachBrokenInnerObsidian(location, credit)) {
                        return;
                    }
                }
            }
        });
    }

    private Player resolveBreachCredit(ActiveHavocBase base, Location breachLoc, Player preferred) {
        if (preferred != null) {
            return preferred;
        }
        World world = breachLoc.getWorld();
        if (world == null) {
            return null;
        }
        double rsq = (double) plugin.getHavocConfig().getRewardRadius() * plugin.getHavocConfig().getRewardRadius();
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(breachLoc) > rsq) {
                continue;
            }
            try {
                Object playerFaction = plugin.getFactionsBridge().getPlayerFaction(player);
                if (playerFaction != null && plugin.getFactionsBridge().factionsEqual(playerFaction, havocFaction)) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            double dist = player.getLocation().distanceSquared(breachLoc);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
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

        eventBus.publish(new BaseBreachedEvent(base, breachLoc, progressionCredit, havocFaction));

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

    public ActiveHavocBase pickRandomActiveForEvent(BaseDifficulty d, UUID exclude) {
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

    // Restores all terrain that are restoring immediately
    private void tickRestores() {
        restoreEngine.tick(plugin, basesById, chunkOwners, eventBus);
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
        File schem = cfg.resolveSchematicFile(d);
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
        base.innerBreachRegion = InnerBreachRegion.fromSchematic(clip);
        logInnerBreachRegion(d, base);

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
        eventBus.publish(new BaseSpawnedEvent(base,
                new Location(world, base.obsidianCenterX, base.obsidianCenterY, base.obsidianCenterZ)));
        return true;
    }

    private void logInnerBreachRegion(BaseDifficulty d, ActiveHavocBase base) {
        if (base.innerBreachRegion == null || base.innerBreachRegion.isEmpty()) {
            plugin.getLogger().warning("No inner obsidian breach shell mapped for " + d + " base ~" + shortId(base.id));
            return;
        }
        HavocDebug.announce(plugin, "Inner breach shell mapped for ~" + shortId(base.id)
                + " (" + base.innerBreachRegion.size() + " obsidian positions).");
    }

}
