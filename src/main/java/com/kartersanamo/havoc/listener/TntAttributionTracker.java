package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.material.Dispenser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TntAttributionTracker {

    private final Havoc plugin;
    private final ConcurrentHashMap<UUID, TntOrigin> tntOrigins = new ConcurrentHashMap<UUID, TntOrigin>();
    private final List<PendingDispense> pendingDispenses = Collections.synchronizedList(new ArrayList<PendingDispense>());

    public TntAttributionTracker(Havoc plugin) {
        this.plugin = plugin;
    }

    void onBlockDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() != Material.DISPENSER) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.TNT) {
            return;
        }
        Dispenser dispenser = (Dispenser) event.getBlock().getState().getData();
        BlockFace face = dispenser.getFacing();
        Block dispenserBlock = event.getBlock();
        Location birthEstimate = dispenserBlock.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
        Object factionAtDispenser;
        try {
            factionAtDispenser = plugin.getFactionsBridge().getFactionAtLocation(dispenserBlock.getLocation());
            if (factionAtDispenser == null || plugin.getFactionsBridge().isWilderness(factionAtDispenser)) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        synchronized (pendingDispenses) {
            pruneExpiredPending();
            pendingDispenses.add(new PendingDispense(System.currentTimeMillis(), birthEstimate, factionAtDispenser));
        }
    }

    void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) {
            return;
        }
        PendingDispense match = null;
        synchronized (pendingDispenses) {
            pruneExpiredPending();
            double best = 4.0D;
            for (int i = 0; i < pendingDispenses.size(); i++) {
                PendingDispense p = pendingDispenses.get(i);
                if (p.birthEstimate.getWorld() == null || event.getLocation().getWorld() == null) {
                    continue;
                }
                if (!p.birthEstimate.getWorld().equals(event.getLocation().getWorld())) {
                    continue;
                }
                double d = p.birthEstimate.distanceSquared(event.getLocation());
                if (d <= best) {
                    best = d;
                    match = p;
                }
            }
            if (match != null) {
                pendingDispenses.remove(match);
            }
        }
        if (match != null) {
            tntOrigins.put(event.getEntity().getUniqueId(), new TntOrigin(match.birthEstimate, match.factionAtDispenser));
        }
    }

    Player resolveExplosionWinner(EntityExplodeEvent event) {
        if (event.getEntity() == null) {
            return null;
        }
        TntOrigin origin = tntOrigins.remove(event.getEntity().getUniqueId());
        if (origin == null || origin.birthLocation == null || origin.birthLocation.getWorld() == null || origin.factionAtDispenser == null) {
            return null;
        }
        World world = origin.birthLocation.getWorld();
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            Object pf;
            try {
                pf = plugin.getFactionsBridge().getPlayerFaction(p);
                if (pf == null || !plugin.getFactionsBridge().factionsEqual(pf, origin.factionAtDispenser)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            double d = p.getLocation().distanceSquared(origin.birthLocation);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void pruneExpiredPending() {
        long cutoff = System.currentTimeMillis() - 5000L;
        Iterator<PendingDispense> it = pendingDispenses.iterator();
        while (it.hasNext()) {
            PendingDispense p = it.next();
            if (p.createdMs < cutoff) {
                it.remove();
            }
        }
    }

    private static final class PendingDispense {
        final long createdMs;
        final Location birthEstimate;
        final Object factionAtDispenser;

        PendingDispense(long createdMs, Location birthEstimate, Object factionAtDispenser) {
            this.createdMs = createdMs;
            this.birthEstimate = birthEstimate;
            this.factionAtDispenser = factionAtDispenser;
        }
    }

    private static final class TntOrigin {
        final Location birthLocation;
        final Object factionAtDispenser;

        TntOrigin(Location birthLocation, Object factionAtDispenser) {
            this.birthLocation = birthLocation;
            this.factionAtDispenser = factionAtDispenser;
        }
    }
}
