package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class ExplosionBreachListener implements Listener {

    private final Havoc plugin;
    private final TntAttributionTracker tntAttributionTracker;

    public ExplosionBreachListener(Havoc plugin, TntAttributionTracker tntAttributionTracker) {
        this.plugin = plugin;
        this.tntAttributionTracker = tntAttributionTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeBreach(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) {
            return;
        }
        Player winner = tntAttributionTracker.resolveExplosionWinner(event);
        plugin.getBaseService().tryRecordRaidParticipation(event.getLocation(), winner);
        plugin.getBaseService().tryBreachFromExplosion(event.blockList(), winner);
    }
}
