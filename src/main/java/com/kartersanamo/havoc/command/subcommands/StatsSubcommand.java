package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import com.kartersanamo.havoc.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class StatsSubcommand implements HavocSubcommand {

    private final Havoc plugin;

    public StatsSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.STATS_VIEW)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        Player viewer = (Player) sender;
        OfflinePlayer target = viewer;
        if (args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            if (!viewer.hasPermission(PermissionNodes.ADMIN_STATS_VIEW)) {
                plugin.getMessages().send(viewer, "command.no-permission");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || target.getUniqueId() == null) {
                plugin.getMessages().sendRaw(viewer, "stats.unknown-player",
                        MessageVars.one(MessageKeys.PLAYER, args[0]));
                return true;
            }
        }
        String targetName = target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName();
        PlayerStats stats = plugin.getPlayerStatsStore().get(target.getUniqueId());
        plugin.getMessages().sendRaw(viewer, "stats.header", MessageVars.one(MessageKeys.PLAYER, targetName));
        plugin.getMessages().sendRaw(viewer, "stats.raids-participated",
                MessageVars.one("value", String.valueOf(stats.raidsParticipated)));
        plugin.getMessages().sendRaw(viewer, "stats.bases-breached",
                MessageVars.one("value", String.valueOf(stats.basesBreached)));
        plugin.getMessages().sendRaw(viewer, "stats.salvage-earned",
                MessageVars.one("value", String.valueOf(stats.salvageEarned)));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission(PermissionNodes.ADMIN_STATS_VIEW)) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partial(names, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> partial(List<String> opts, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
