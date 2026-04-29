package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.command.subcommands.admin.AdminListSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminLogsSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminPerfSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminReloadSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminSalvageSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminSpawnSubcommand;
import com.kartersanamo.havoc.command.subcommands.admin.AdminSubcommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminSubcommandRouter implements HavocSubcommand {

    private final Havoc plugin;
    private final Map<String, AdminSubcommand> routes = new LinkedHashMap<String, AdminSubcommand>();

    public AdminSubcommandRouter(Havoc plugin) {
        this.plugin = plugin;
        register(new AdminListSubcommand(plugin));
        register(new AdminReloadSubcommand(plugin));
        register(new AdminSpawnSubcommand(plugin));
        register(new AdminSalvageSubcommand(plugin));
        register(new AdminLogsSubcommand(plugin));
        register(new AdminPerfSubcommand(plugin));
    }

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("havoc.admin")) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.getMessages().send(sender, "command.admin-usage");
            return true;
        }
        AdminSubcommand sub = routes.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.getMessages().send(sender, "command.unknown-subcommand");
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, rest);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("havoc.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return CommandUtil.partial(Arrays.asList("list", "reload", "spawn", "salvage", "logs", "perf"), args[0]);
        }
        AdminSubcommand sub = routes.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            return Collections.emptyList();
        }
        return sub.tabComplete(sender, args);
    }

    private void register(AdminSubcommand sub) {
        routes.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }
}
