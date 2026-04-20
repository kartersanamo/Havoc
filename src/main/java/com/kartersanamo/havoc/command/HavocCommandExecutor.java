package com.kartersanamo.havoc.command;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.config.HavocConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HavocCommandExecutor implements CommandExecutor, TabCompleter {

    private final Havoc plugin;

    public HavocCommandExecutor(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().sendList(sender, "command.help.player", null);
            if (sender.hasPermission("havoc.admin")) {
                plugin.getMessages().sendList(sender, "command.help.admin", null);
            }
            return true;
        }
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if ("shop".equals(a0)) {
            if (!(sender instanceof Player)) {
                plugin.getMessages().send(sender, "command.players-only");
                return true;
            }
            plugin.getSalvageShop().open((Player) sender);
            return true;
        }
        if ("salvage".equals(a0)) {
            if (!(sender instanceof Player)) {
                plugin.getMessages().send(sender, "command.players-only");
                return true;
            }
            int bal = plugin.getSalvageStore().get(((Player) sender).getUniqueId());
            plugin.getMessages().send(sender, "command.salvage-balance", one("balance", String.valueOf(bal)));
            return true;
        }
        if ("admin".equals(a0)) {
            if (!sender.hasPermission("havoc.admin")) {
                plugin.getMessages().send(sender, "command.no-permission");
                return true;
            }
            if (args.length < 2) {
                plugin.getMessages().send(sender, "command.admin-usage");
                return true;
            }
            String a1 = args[1].toLowerCase(Locale.ROOT);
            if ("list".equals(a1)) {
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
            if ("reload".equals(a1)) {
                HavocConfig c = plugin.getHavocConfig();
                c.reload();
                plugin.getMessages().reload();
                plugin.getSalvageShop().reload();
                plugin.getSalvageStore().load();
                plugin.getProgressionStore().load();
                plugin.applyWorldBorder();
                plugin.getMessages().send(sender, "admin.reload.success");
                return true;
            }
            if ("spawn".equals(a1)) {
                if (args.length < 3) {
                    plugin.getMessages().send(sender, "admin.spawn.usage");
                    return true;
                }
                BaseDifficulty d;
                try {
                    d = BaseDifficulty.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getMessages().send(sender, "admin.spawn.invalid-tier");
                    return true;
                }
                boolean ok = plugin.getBaseService().trySpawnOne(d);
                plugin.getMessages().send(sender, ok ? "admin.spawn.success" : "admin.spawn.failed", one("difficulty", d.name()));
                return true;
            }
            if ("salvage".equals(a1)) {
                if (args.length < 4) {
                    plugin.getMessages().send(sender, "admin.salvage.usage");
                    return true;
                }
                String mode = args[2].toLowerCase(Locale.ROOT);
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    plugin.getMessages().send(sender, "admin.salvage.player-not-found", one("player", args[3]));
                    return true;
                }
                if ("show".equals(mode)) {
                    int bal = plugin.getSalvageStore().get(target.getUniqueId());
                    Map<String, String> vars = new HashMap<String, String>();
                    vars.put("player", target.getName() == null ? args[3] : target.getName());
                    vars.put("balance", String.valueOf(bal));
                    plugin.getMessages().send(sender, "admin.salvage.show", vars);
                    return true;
                }
                if (!"add".equals(mode) && !"set".equals(mode)) {
                    plugin.getMessages().send(sender, "admin.salvage.invalid-action");
                    return true;
                }
                if (args.length < 5) {
                    plugin.getMessages().send(sender, "admin.salvage.amount-required");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    plugin.getMessages().send(sender, "admin.salvage.invalid-amount");
                    return true;
                }
                if ("set".equals(mode) && amount < 0) {
                    plugin.getMessages().send(sender, "admin.salvage.invalid-amount");
                    return true;
                }
                if ("add".equals(mode)) {
                    plugin.getSalvageStore().add(target.getUniqueId(), amount);
                } else {
                    plugin.getSalvageStore().set(target.getUniqueId(), amount);
                }
                int bal = plugin.getSalvageStore().get(target.getUniqueId());
                Map<String, String> vars = new HashMap<String, String>();
                vars.put("player", target.getName() == null ? args[3] : target.getName());
                vars.put("amount", String.valueOf(amount));
                vars.put("balance", String.valueOf(bal));
                plugin.getMessages().send(sender, "add".equals(mode) ? "admin.salvage.add-success" : "admin.salvage.set-success", vars);
                return true;
            }
        }
        plugin.getMessages().send(sender, "command.unknown-subcommand");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.asList("shop", "salvage", "admin"), args[0]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("list", "reload", "spawn", "salvage"), args[1]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("EASY", "MEDIUM", "HARD"), args[2]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "salvage".equalsIgnoreCase(args[1]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("add", "set", "show"), args[2]);
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(args[0]) && "salvage".equalsIgnoreCase(args[1]) && sender.hasPermission("havoc.admin")) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partial(names, args[3]);
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

    private static Map<String, String> one(String key, String value) {
        Map<String, String> out = new HashMap<String, String>();
        out.put(key, value);
        return out;
    }
}
