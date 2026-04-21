package com.kartersanamo.havoc.command;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.audit.HavocLogService;
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
                plugin.getLogService().log("ADMIN_RELOAD", sender.getName(), "", null, "reload configs");
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
                plugin.getLogService().log("ADMIN_SPAWN_" + (ok ? "SUCCESS" : "FAILED"), sender.getName(), "", null,
                        "difficulty=" + d.name());
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
                plugin.getLogService().log("ADMIN_SALVAGE_" + mode.toUpperCase(Locale.ROOT),
                        sender.getName(), "", null,
                        "target=" + vars.get("player") + ", amount=" + amount + ", balance=" + bal);
                return true;
            }
            if ("logs".equals(a1)) {
                handleLogsCommand(sender, args);
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
            return partial(Arrays.asList("list", "reload", "spawn", "salvage", "logs"), args[1]);
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
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "logs".equalsIgnoreCase(args[1]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("user", "base", "1", "2", "3"), args[2]);
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(args[0]) && "logs".equalsIgnoreCase(args[1]) && "user".equalsIgnoreCase(args[2]) && sender.hasPermission("havoc.admin")) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partial(names, args[3]);
        }
        return Collections.emptyList();
    }

    private void handleLogsCommand(CommandSender sender, String[] args) {
        List<HavocLogService.Entry> logs;
        String scope;
        int page = 1;
        if (args.length == 2) {
            logs = plugin.getLogService().queryAllNewestFirst();
            scope = "all";
        } else {
            String mode = args[2].toLowerCase(Locale.ROOT);
            if (isInt(mode)) {
                logs = plugin.getLogService().queryAllNewestFirst();
                scope = "all";
                page = Integer.parseInt(mode);
            } else if ("user".equals(mode)) {
                if (args.length < 4) {
                    plugin.getMessages().send(sender, "admin.logs.usage");
                    return;
                }
                String user = args[3];
                logs = plugin.getLogService().queryByUserNewestFirst(user);
                scope = "user:" + user;
                if (args.length >= 5 && isInt(args[4])) {
                    page = Integer.parseInt(args[4]);
                }
            } else if ("base".equals(mode)) {
                if (args.length < 4) {
                    plugin.getMessages().send(sender, "admin.logs.usage");
                    return;
                }
                String base = args[3];
                logs = plugin.getLogService().queryByBaseNewestFirst(base);
                scope = "base:" + base;
                if (args.length >= 5 && isInt(args[4])) {
                    page = Integer.parseInt(args[4]);
                }
            } else {
                plugin.getMessages().send(sender, "admin.logs.usage");
                return;
            }
        }
        if (logs.isEmpty()) {
            plugin.getMessages().send(sender, "admin.logs.empty");
            return;
        }
        int perPage = 8;
        int pages = Math.max(1, (logs.size() + perPage - 1) / perPage);
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        int from = (page - 1) * perPage;
        int to = Math.min(logs.size(), from + perPage);
        Map<String, String> hdr = new HashMap<String, String>();
        hdr.put("scope", scope);
        hdr.put("page", String.valueOf(page));
        hdr.put("pages", String.valueOf(pages));
        hdr.put("count", String.valueOf(logs.size()));
        plugin.getMessages().send(sender, "admin.logs.header", hdr);
        for (int i = from; i < to; i++) {
            sender.sendMessage(plugin.getLogService().formatForChat(logs.get(i)));
        }
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

    private static boolean isInt(String raw) {
        try {
            Integer.parseInt(raw);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
