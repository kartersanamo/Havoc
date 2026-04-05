package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.storage.ProgressionStore;
import com.kartersanamo.havoc.storage.SalvageStore;
import com.kartersanamo.havoc.world.ColumnBoxSnapshot;
import com.kartersanamo.havoc.world.SchematicService;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.data.DataException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BaseService {

    private final Havoc plugin;
    private final Random random = new Random();
    private final Map<ChunkKey, UUID> chunkOwners = new ConcurrentHashMap<ChunkKey, UUID>();
    private final Map<UUID, ActiveHavocBase> basesById = new ConcurrentHashMap<UUID, ActiveHavocBase>();
    private Object havocFaction;
    private int restoreTaskId = -1;
    private int maintainerTaskId = -1;

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
        }, 40L, 200L);
        restoreTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tickRestores();
            }
        }, 1L, 1L);
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
            for (int i = have; i < want; i++) {
                if (!trySpawnOne(d)) {
                    HavocDebug.announce(plugin, "Population: could not spawn " + d + " (have " + have + ", want " + want + ") — see console / check border & schematic.");
                    break;
                }
            }
        }
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

        int salvageAmt = cfg.randomSalvage(base.difficulty);
        SalvageStore salvage = plugin.getSalvageStore();
        ProgressionStore prog = plugin.getProgressionStore();
        List<Player> rewarded = new ArrayList<Player>();
        double rsq = (double) cfg.getRewardRadius() * cfg.getRewardRadius();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(breachLoc) > rsq) {
                continue;
            }
            try {
                Object pf = plugin.getFactionsBridge().getPlayerFaction(p);
                if (pf != null && plugin.getFactionsBridge().factionsEqual(pf, havocFaction)) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            rewarded.add(p);
        }
        HavocDebug.announce(plugin, "Rewards: " + rewarded.size() + " player(s) in radius, Salvage roll " + salvageAmt + " each.");

        BaseDifficulty nextTier = base.difficulty;
        if (progressionCredit != null) {
            nextTier = prog.nextHintDifficulty(progressionCredit.getUniqueId(), base.difficulty);
            HavocDebug.announce(plugin, "Progression credit: " + progressionCredit.getName() + " → next tier hint " + nextTier + ".");
        }
        final ActiveHavocBase target = pickRandomActive(nextTier, base.id);
        for (Player p : rewarded) {
            salvage.add(p.getUniqueId(), salvageAmt);
            p.sendMessage(ChatColor.GOLD + "+" + salvageAmt + " Salvage");
            if (target != null) {
                Location l = new Location(Bukkit.getWorld(target.worldName), target.obsidianCenterX, target.obsidianCenterY + 2, target.obsidianCenterZ);
                p.sendMessage(ChatColor.AQUA + "Next Havoc lead (" + nextTier + "): " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            } else {
                p.sendMessage(ChatColor.GRAY + "No active " + nextTier + " Havoc base to point you to yet.");
            }
        }
        salvage.save();
        prog.save();

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
        for (ActiveHavocBase b : new ArrayList<ActiveHavocBase>(basesById.values())) {
            if (b.state != BaseState.RESTORING || b.terrainSnapshot == null) {
                continue;
            }
            World w = Bukkit.getWorld(b.worldName);
            if (w == null) {
                continue;
            }
            HavocConfig cfg = plugin.getHavocConfig();
            int vol = b.terrainSnapshot.volume();
            int ticks = Math.max(1, cfg.getRestoreSeconds() * 20);
            int perTick = (vol + ticks - 1) / ticks;
            b.terrainSnapshot.applyPartial(w, b.restoreCursor, perTick);
            b.restoreCursor += perTick;
            if (b.restoreCursor >= vol) {
                HavocDebug.announce(plugin, "Terrain restore DONE for ~" + shortId(b.id) + " — unclaiming " + b.claimedChunks.size() + " chunk(s).");
                finishRestore(b, w);
                unregisterChunks(b);
                basesById.remove(b.id);
            }
        }
    }

    private void finishRestore(ActiveHavocBase b, World w) {
        try {
            for (ChunkKey key : b.claimedChunks) {
                if (!key.getWorld().equals(w.getName())) {
                    continue;
                }
                plugin.getFactionsBridge().unclaimChunk(w.getChunkAt(key.getX(), key.getZ()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Unclaim after restore: " + e.getMessage());
        }
    }

    private void unregisterChunks(ActiveHavocBase b) {
        for (ChunkKey key : b.claimedChunks) {
            chunkOwners.remove(key);
        }
        b.claimedChunks.clear();
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
        int w = clip.getWidth();
        int h = clip.getHeight();
        int len = clip.getLength();
        HavocDebug.announce(plugin, "Loaded " + d + " schematic size " + w + " x " + h + " x " + len + ".");

        int half = cfg.getBorderHalfSize();
        int minC = (-half + 32) / 16;
        int maxC = (half - 32) / 16;
        int sep = cfg.getMinCenterSeparationChunks();
        int[] off = cfg.getSchematicCenterOffset(d);
        for (int attempt = 0; attempt < 80; attempt++) {
            int cx = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            int cz = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            int targetX = cx * 16 + 8;
            int targetZ = cz * 16 + 8;
            int targetY = cfg.getPasteCenterWorldY();
            int originX = targetX - off[0];
            int originY = targetY - off[1];
            int originZ = targetZ - off[2];
            int worldH = world.getMaxHeight();
            int yMax = worldH - h;
            if (originY < 0) {
                HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": clamp paste Y " + originY + " → 0 (under world).");
                originY = 0;
            }
            if (originY > yMax) {
                HavocDebug.announce(plugin, "Spawn attempt " + attempt + ": clamp paste Y " + originY + " → " + yMax + " (over height).");
                originY = yMax;
            }
            int obsWorldY = originY + off[1];
            if (!fitsInBorder(originX, originZ, w, len, half)) {
                continue;
            }
            int rNew = footprintChunkRadius(originX, originZ, w, len, cx, cz);
            if (!farEnough(cx, cz, sep, rNew)) {
                continue;
            }
            if (spawnAt(world, cx, cz, d, clip, originX, originY, originZ, w, h, len, targetX, obsWorldY, targetZ, rNew)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fitsInBorder(int originX, int originZ, int w, int len, int half) {
        return originX >= -half && originX + w - 1 <= half && originZ >= -half && originZ + len - 1 <= half;
    }

    /**
     * Chebyshev chunk radius from center chunk to farthest chunk touched by the footprint.
     */
    private static int footprintChunkRadius(int originX, int originZ, int w, int len, int centerChunkX, int centerChunkZ) {
        int minCx = Math.floorDiv(originX, 16);
        int maxCx = Math.floorDiv(originX + w - 1, 16);
        int minCz = Math.floorDiv(originZ, 16);
        int maxCz = Math.floorDiv(originZ + len - 1, 16);
        int r = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                r = Math.max(r, Math.max(Math.abs(cx - centerChunkX), Math.abs(cz - centerChunkZ)));
            }
        }
        return r;
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

    private boolean spawnAt(World world, int centerChunkX, int centerChunkZ, BaseDifficulty d, CuboidClipboard clip,
            int originX, int originY, int originZ, int w, int h, int len,
            int obsidianCenterX, int obsidianCenterY, int obsidianCenterZ, int chunkFootprintRadius) {
        HavocConfig cfg = plugin.getHavocConfig();
        ActiveHavocBase base = new ActiveHavocBase(d, world.getName(), centerChunkX, centerChunkZ);
        base.pasteOriginX = originX;
        base.pasteOriginY = originY;
        base.pasteOriginZ = originZ;
        base.footprintSizeX = w;
        base.footprintSizeZ = len;
        base.chunkFootprintRadius = chunkFootprintRadius;
        base.obsidianCenterX = obsidianCenterX;
        base.obsidianCenterY = obsidianCenterY;
        base.obsidianCenterZ = obsidianCenterZ;

        int minCx = Math.floorDiv(originX, 16);
        int maxCx = Math.floorDiv(originX + w - 1, 16);
        int minCz = Math.floorDiv(originZ, 16);
        int maxCz = Math.floorDiv(originZ + len - 1, 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load();
                }
            }
        }
        HavocDebug.announce(plugin, "Preparing snapshot " + w + "x" + len + " columns full height @ " + originX + "," + originZ + " …");

        int worldH = world.getMaxHeight();
        ColumnBoxSnapshot snap = ColumnBoxSnapshot.capture(world, originX, originZ, w, len, worldH);
        base.terrainSnapshot = snap;

        SchematicService paster = new SchematicService();
        try {
            HavocDebug.announce(plugin, "Pasting " + d + " @ min " + originX + "," + originY + "," + originZ + " (obsidian center " + obsidianCenterX + "," + obsidianCenterY + "," + obsidianCenterZ + ").");
            paster.paste(world, clip, originX, originY, originZ);
        } catch (IOException | DataException | MaxChangedBlocksException e) {
            plugin.getLogger().severe("Schematic paste failed: " + e.getMessage());
            HavocDebug.announce(plugin, "PASTE FAILED: " + e.getMessage());
            snap.applyAll(world);
            return false;
        }
        try {
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    Chunk ch = world.getChunkAt(cx, cz);
                    plugin.getFactionsBridge().claimChunkForFaction(ch, havocFaction);
                    base.claimedChunks.add(ChunkKey.of(ch));
                }
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
        HavocDebug.announce(plugin, "Spawned " + d + " base ~" + shortId(base.id) + " chunks " + minCx + "," + minCz + " → " + maxCx + "," + maxCz
                + " (center chunk " + centerChunkX + "," + centerChunkZ + ", claims=" + base.claimedChunks.size() + ").");
        plugin.getLogger().info("Spawned " + d + " Havoc base ~" + shortId(base.id) + " at chunk " + centerChunkX + "," + centerChunkZ);
        return true;
    }
}
