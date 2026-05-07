package com.kartersanamo.havoc.command;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.command.subcommands.AdminSubcommandRouter;
import com.kartersanamo.havoc.command.subcommands.HavocSubcommand;
import com.kartersanamo.havoc.command.subcommands.LeaderboardsSubcommand;
import com.kartersanamo.havoc.command.subcommands.SalvageBalanceSubcommand;
import com.kartersanamo.havoc.command.subcommands.ShopSubcommand;
import com.kartersanamo.havoc.command.subcommands.StatsSubcommand;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HavocCommandExecutor implements CommandExecutor, TabCompleter {

    private final Havoc plugin;
    private final Map<String, HavocSubcommand> routes = new LinkedHashMap<String, HavocSubcommand>();

    public HavocCommandExecutor(Havoc plugin) {
        this.plugin = plugin;
        register(new ShopSubcommand(plugin));
        register(new SalvageBalanceSubcommand(plugin));
        register(new StatsSubcommand(plugin));
        register(new LeaderboardsSubcommand(plugin));
        register(new AdminSubcommandRouter(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PermissionNodes.USE)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 0) {
            if (sender.hasPermission(PermissionNodes.SHOP_OPEN) || sender.hasPermission(PermissionNodes.SALVAGE_BALANCE)) {
                plugin.getMessages().sendList(sender, "command.help.player", null);
            }
            if (sender.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getMessages().sendList(sender, "command.help.admin", null);
            }
            return true;
        }
        HavocSubcommand sub = routes.get(args[0].toLowerCase(Locale.ROOT));
        if (sub != null) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            return sub.execute(sender, rest);
        }
        plugin.getMessages().send(sender, "command.unknown-subcommand");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PermissionNodes.USE)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> opts = new ArrayList<String>();
            if (sender.hasPermission(PermissionNodes.SHOP_OPEN)) {
                opts.add("shop");
            }
            if (sender.hasPermission(PermissionNodes.SALVAGE_BALANCE)) {
                opts.add("salvage");
            }
            if (sender.hasPermission(PermissionNodes.STATS_VIEW)) {
                opts.add("stats");
            }
            if (sender.hasPermission(PermissionNodes.LEADERBOARDS_VIEW)) {
                opts.add("leaderboards");
            }
            if (sender.hasPermission(PermissionNodes.ADMIN)) {
                opts.add("admin");
            }
            return partial(opts, args[0]);
        }
        HavocSubcommand sub = routes.get(args[0].toLowerCase(Locale.ROOT));
        if (sub != null) {
            return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return Collections.emptyList();
    }

    private static List<String> partial(List<String> opts, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    private void register(HavocSubcommand sub) {
        routes.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }
}
