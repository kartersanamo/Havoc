package com.kartersanamo.havoc.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

public final class TntSpawnListener implements Listener {

    private final TntAttributionTracker tntAttributionTracker;

    public TntSpawnListener(TntAttributionTracker tntAttributionTracker) {
        this.tntAttributionTracker = tntAttributionTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        tntAttributionTracker.onTntSpawn(event);
    }
}
