package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidLockNotifier {

    private final Havoc plugin;
    private final ConcurrentHashMap<UUID, Long> notifyCooldownMs = new ConcurrentHashMap<UUID, Long>();

    public RaidLockNotifier(Havoc plugin) {
        this.plugin = plugin;
    }

    void notifyLocked(Player player, String key) {
        long now = System.currentTimeMillis();
        Long prev = notifyCooldownMs.get(player.getUniqueId());
        if (prev != null && now - prev < plugin.getHavocConfig().getLockNotifyCooldownMs()) {
            return;
        }
        notifyCooldownMs.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, key);
    }
}
