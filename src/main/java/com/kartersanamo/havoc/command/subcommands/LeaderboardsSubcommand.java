package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.permission.PermissionNodes;
import com.kartersanamo.havoc.stats.PlayerStatsStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LeaderboardsSubcommand implements HavocSubcommand {

    private final Havoc plugin;

    public LeaderboardsSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "leaderboards";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.LEADERBOARDS_VIEW)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        PlayerStatsStore.LeaderboardPeriod period = PlayerStatsStore.LeaderboardPeriod.LIFETIME;
        if (args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            PlayerStatsStore.LeaderboardPeriod parsed = parsePeriod(args[0]);
            if (parsed == null) {
                sender.sendMessage("§cUsage: /havoc leaderboards [lifetime|weekly|monthly]");
                return true;
            }
            period = parsed;
        }
        plugin.getLeaderboardGui().open((Player) sender, PlayerStatsStore.LeaderboardMetric.SALVAGE_EARNED, period, 0);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(java.util.Arrays.asList("lifetime", "weekly", "monthly"), args[0]);
        }
        return Collections.emptyList();
    }

    private PlayerStatsStore.LeaderboardPeriod parsePeriod(String raw) {
        String p = raw.trim().toLowerCase(Locale.ROOT);
        if ("lifetime".equals(p)) {
            return PlayerStatsStore.LeaderboardPeriod.LIFETIME;
        }
        if ("weekly".equals(p)) {
            return PlayerStatsStore.LeaderboardPeriod.WEEKLY;
        }
        if ("monthly".equals(p)) {
            return PlayerStatsStore.LeaderboardPeriod.MONTHLY;
        }
        return null;
    }

    private List<String> partial(List<String> opts, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
