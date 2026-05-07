package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

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
        plugin.getLeaderboardGui().open((Player) sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
