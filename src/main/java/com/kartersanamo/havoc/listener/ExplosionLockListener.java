package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

public final class ExplosionLockListener implements Listener {

    private final Havoc plugin;

    public ExplosionLockListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplodeLock(EntityExplodeEvent event) {
        BaseService bases = plugin.getBaseService();
        List<org.bukkit.block.Block> blocks = event.blockList();
        int removed = 0;
        for (Iterator<org.bukkit.block.Block> it = blocks.iterator(); it.hasNext();) {
            org.bukkit.block.Block b = it.next();
            if (bases.shouldCancelBlockChange(b.getLocation())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().fine("[Havoc] Explosion: removed " + removed + " block(s) from break list (restore / satellite lock).");
        }
    }
}
