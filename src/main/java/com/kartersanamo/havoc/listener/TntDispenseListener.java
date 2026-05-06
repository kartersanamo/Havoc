package com.kartersanamo.havoc.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;

public final class TntDispenseListener implements Listener {

    private final TntAttributionTracker tntAttributionTracker;

    public TntDispenseListener(TntAttributionTracker tntAttributionTracker) {
        this.tntAttributionTracker = tntAttributionTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        tntAttributionTracker.onBlockDispense(event);
    }
}
