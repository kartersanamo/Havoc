package com.kartersanamo.havoc.debug;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loud in-game announcements plus console lines for troubleshooting.
 */
public final class HavocDebug {

    private HavocDebug() {
    }

    public static void announce(JavaPlugin plugin, String message) {
        plugin.getLogger().info("[Havoc] " + message);
        if (!(plugin instanceof Havoc)) {
            return;
        }
        Havoc h = (Havoc) plugin;
        if (!h.getHavocConfig().isDebugBroadcastGame()) {
            return;
        }
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[Havoc] " + ChatColor.GRAY + message);
    }
}
