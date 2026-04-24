package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminListSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminListSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            plugin.getBaseAdminGui().openList((Player) sender);
            return true;
        }
        List<ActiveHavocBase> bases = plugin.getBaseService().listAllBasesSorted();
        if (bases.isEmpty()) {
            plugin.getMessages().send(sender, "admin.list.empty");
            return true;
        }
        plugin.getMessages().send(sender, "admin.list.header", one("count", String.valueOf(bases.size())));
        for (ActiveHavocBase b : bases) {
            String shortId = b.id.toString().substring(0, 8);
            Map<String, String> vars = new HashMap<String, String>();
            vars.put("difficulty", b.difficulty.name());
            vars.put("state", b.state.name());
            vars.put("world", b.worldName);
            vars.put("x", String.valueOf(b.obsidianCenterX));
            vars.put("y", String.valueOf(b.obsidianCenterY));
            vars.put("z", String.valueOf(b.obsidianCenterZ));
            vars.put("id", shortId);
            vars.put("claims", String.valueOf(b.claimedChunks.size()));
            plugin.getMessages().send(sender, "admin.list.line", vars);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<String>();
    }

    private static Map<String, String> one(String key, String value) {
        Map<String, String> out = new HashMap<String, String>();
        out.put(key, value);
        return out;
    }
}
