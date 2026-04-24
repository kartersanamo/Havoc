package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.command.subcommands.CommandUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AdminSpawnSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminSpawnSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "admin.spawn.usage");
            return true;
        }
        BaseDifficulty d;
        try {
            d = BaseDifficulty.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getMessages().send(sender, "admin.spawn.invalid-tier");
            return true;
        }
        boolean ok = plugin.getBaseService().trySpawnOne(d);
        plugin.getMessages().send(sender, ok ? "admin.spawn.success" : "admin.spawn.failed", CommandUtil.one("difficulty", d.name()));
        plugin.getLogService().log("ADMIN_SPAWN_" + (ok ? "SUCCESS" : "FAILED"), sender.getName(), "", null,
                "difficulty=" + d.name());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandUtil.partial(Arrays.asList("EASY", "MEDIUM", "HARD"), args[1]);
        }
        return java.util.Collections.emptyList();
    }
}
