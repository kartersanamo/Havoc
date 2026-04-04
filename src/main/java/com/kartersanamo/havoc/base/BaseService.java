package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.storage.ProgressionStore;
import com.kartersanamo.havoc.storage.SalvageStore;
import com.kartersanamo.havoc.world.ColumnBoxSnapshot;
import com.kartersanamo.havoc.world.SchematicService;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.data.DataException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
import java.util.function.BiConsumer;

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
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Could not resolve Havoc faction: " + e.getMessage());
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
    }

    public void shutdown() {
        if (maintainerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(maintainerTaskId);
        }
        if (restoreTaskId != -1) {
            Bukkit.getScheduler().cancelTask(restoreTaskId);
        }
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

    /**
     * All tracked bases (active and restoring), sorted by world then center chunk.
     */
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
        long now = System.currentTimeMillis();
        base.raidEndMs = now + cfg.getRestoreSeconds() * 1000L;
        base.satellite = SatelliteRing.capture(world, base.centerChunkX, base.centerChunkZ, cfg.getWatchChunkRadius());
        base.restoreCursor = 0;

        Location spawn = cfg.getSpawnLocation();
        for (Player p : world.getPlayers()) {
            if (!base.containsBlockColumn(p.getLocation().getBlockX(), p.getLocation().getBlockZ())) {
                continue;
            }
            try {
                Object at = plugin.getFactionsBridge().getFactionAtLocation(p.getLocation());
                if (at != null && plugin.getFactionsBridge().factionsEqual(at, havocFaction)) {
                    p.teleport(spawn);
                }
            } catch (Exception ignored) {
            }
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
        BaseDifficulty nextTier = base.difficulty;
        if (progressionCredit != null) {
            nextTier = prog.nextHintDifficulty(progressionCredit.getUniqueId(), base.difficulty);
        }
        final ActiveHavocBase target = pickRandomActive(nextTier, base.id);
        for (Player p : rewarded) {
            salvage.add(p.getUniqueId(), salvageAmt);
            p.sendMessage(ChatColor.GOLD + "+" + salvageAmt + " Salvage");
            if (target != null) {
                Location l = new Location(Bukkit.getWorld(target.worldName), target.minBlockX + 8, cfg.getPasteFloorY() + 2, target.minBlockZ + 8);
                p.sendMessage(ChatColor.AQUA + "Next Havoc lead (" + nextTier + "): " + l.getBlockX() + ", " + l.getBlockZ());
            } else {
                p.sendMessage(ChatColor.GRAY + "No active " + nextTier + " Havoc base to point you to yet.");
            }
        }
        salvage.save();
        prog.save();

        long delay = cfg.getRestoreSeconds() * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (base.satellite != null) {
                    try {
                        base.satellite.restoreAndUnclaimAll(world, plugin.getFactionsBridge());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Satellite reset failed: " + e.getMessage());
                    }
                }
            }
        }.runTaskLater(plugin, delay);
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
                finishRestore(b, w);
                unregisterChunks(b);
                basesById.remove(b.id);
            }
        }
    }

    private void finishRestore(ActiveHavocBase b, World w) {
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Chunk ch = w.getChunkAt(b.centerChunkX + dx, b.centerChunkZ + dz);
                    plugin.getFactionsBridge().unclaimChunk(ch);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Unclaim after restore: " + e.getMessage());
        }
    }

    private void unregisterChunks(ActiveHavocBase b) {
        b.registerChunks(new BiConsumer<Integer, Integer>() {
            @Override
            public void accept(Integer cx, Integer cz) {
                chunkOwners.remove(ChunkKey.of(Bukkit.getWorld(b.worldName), cx, cz));
            }
        });
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
            return false;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Configured world not loaded: " + cfg.getWorldName());
            return false;
        }
        int half = cfg.getBorderHalfSize();
        int minC = (-half + 32) / 16;
        int maxC = (half - 32) / 16;
        int sep = cfg.getMinCenterSeparationChunks();
        for (int attempt = 0; attempt < 80; attempt++) {
            int cx = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            int cz = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
            if (!farEnough(cx, cz, sep)) {
                continue;
            }
            if (spawnAt(world, cx, cz, d)) {
                return true;
            }
        }
        return false;
    }

    private boolean farEnough(int cx, int cz, int sep) {
        for (ActiveHavocBase b : basesById.values()) {
            int d = Math.max(Math.abs(b.centerChunkX - cx), Math.abs(b.centerChunkZ - cz));
            if (d < sep) {
                return false;
            }
        }
        return true;
    }

    private boolean spawnAt(World world, int centerChunkX, int centerChunkZ, BaseDifficulty d) {
        HavocConfig cfg = plugin.getHavocConfig();
        ActiveHavocBase base = new ActiveHavocBase(d, world.getName(), centerChunkX, centerChunkZ);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk ch = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                if (!ch.isLoaded()) {
                    ch.load();
                }
            }
        }
        int minX = base.minBlockX;
        int minZ = base.minBlockZ;
        int h = world.getMaxHeight();
        ColumnBoxSnapshot snap = ColumnBoxSnapshot.capture(world, minX, minZ, 48, 48, h);
        base.terrainSnapshot = snap;
        File schem = new File(plugin.getDataFolder(), cfg.getSchematicsFolder() + "/" + cfg.schematicFileName(d));
        if (!schem.isFile()) {
            plugin.getLogger().warning("Missing schematic: " + schem.getAbsolutePath());
            return false;
        }
        SchematicService paster = new SchematicService();
        try {
            paster.paste(world, schem, minX, cfg.getPasteFloorY(), minZ);
        } catch (IOException | DataException | MaxChangedBlocksException e) {
            plugin.getLogger().severe("Schematic paste failed: " + e.getMessage());
            snap.applyAll(world);
            return false;
        }
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Chunk ch = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                    plugin.getFactionsBridge().claimChunkForFaction(ch, havocFaction);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Claim failed, reverting terrain: " + e.getMessage());
            snap.applyAll(world);
            return false;
        }
        basesById.put(base.id, base);
        registerChunks(base);
        plugin.getLogger().info("Spawned " + d + " Havoc base at chunk " + centerChunkX + "," + centerChunkZ);
        return true;
    }

    private void registerChunks(ActiveHavocBase b) {
        b.registerChunks(new BiConsumer<Integer, Integer>() {
            @Override
            public void accept(Integer cx, Integer cz) {
                chunkOwners.put(ChunkKey.of(Bukkit.getWorld(b.worldName), cx, cz), b.id);
            }
        });
    }
}
