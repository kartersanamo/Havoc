package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.audit.HavocLogEntry;
import com.kartersanamo.havoc.audit.HavocLogService;
import com.kartersanamo.havoc.command.subcommands.CommandUtil;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AdminLogsSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminLogsSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "logs";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_LOGS;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<HavocLogEntry> logs;
        String scope;
        int page = 1;
        if (args.length == 0) {
            logs = plugin.getLogService().queryAllNewestFirst();
            scope = "all";
        } else {
            String mode = args[0].toLowerCase(Locale.ROOT);
            if (CommandUtil.isInt(mode)) {
                logs = plugin.getLogService().queryAllNewestFirst();
                scope = "all";
                page = Integer.parseInt(mode);
            } else if ("export".equals(mode)) {
                if (!sender.hasPermission(PermissionNodes.ADMIN_LOGS_EXPORT)) {
                    plugin.getMessages().send(sender, "command.no-permission");
                    return true;
                }
                return handleExport(sender, args);
            } else {
                AdminLogsParsedFilter f = parseCombinedLogsFilter(args, 0);
                if (!f.ok) {
                    plugin.getMessages().send(sender, f.errorKey == null ? "admin.logs.usage" : f.errorKey);
                    return true;
                }
                logs = plugin.getLogService().queryFilteredNewestFirst(f.user, f.base, f.type, f.from, f.to);
                scope = f.scope;
                page = f.page;
            }
        }
        if (logs.isEmpty()) {
            plugin.getMessages().send(sender, "admin.logs.empty");
            return true;
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
        java.util.Map<String, String> hdr = MessageVars.create()
                .put(MessageKeys.SCOPE, scope)
                .put(MessageKeys.PAGE, page)
                .put(MessageKeys.PAGES, pages)
                .put(MessageKeys.COUNT, logs.size())
                .build();
        plugin.getMessages().send(sender, "admin.logs.header", hdr);
        for (int i = from; i < to; i++) {
            sender.sendMessage(plugin.getLogService().formatForChat(logs.get(i)));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandUtil.partial(Arrays.asList("user", "base", "type", "date", "export", "page", "today", "yesterday", "last7d", "1", "2", "3"), args[1]);
        }
        if (args.length == 3 && "user".equalsIgnoreCase(args[1])) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return CommandUtil.partial(names, args[2]);
        }
        if (args.length == 3 && "type".equalsIgnoreCase(args[1])) {
            return CommandUtil.partial(Arrays.asList("BASE_BREACH", "BASE_SPAWN", "BASE_RESTORE_DONE", "ADMIN_RELOAD", "ADMIN_SALVAGE_ADD", "ADMIN_SALVAGE_SET"), args[2]);
        }
        if (args.length == 3 && "date".equalsIgnoreCase(args[1])) {
            return CommandUtil.partial(Arrays.asList("today", "yesterday", "last7d", "2026-05-04"), args[2]);
        }
        return java.util.Collections.emptyList();
    }

    private boolean handleExport(CommandSender sender, String[] args) {
        List<HavocLogEntry> data;
        String exportedScope;
        if (args.length == 1 || "all".equalsIgnoreCase(args[1])) {
            data = plugin.getLogService().queryAllNewestFirst();
            exportedScope = "all";
        } else if ("user".equalsIgnoreCase(args[1]) && args.length >= 3) {
            data = plugin.getLogService().queryByUserNewestFirst(args[2]);
            exportedScope = "user:" + args[2];
        } else if ("base".equalsIgnoreCase(args[1]) && args.length >= 3) {
            data = plugin.getLogService().queryByBaseNewestFirst(args[2]);
            exportedScope = "base:" + args[2];
        } else if ("type".equalsIgnoreCase(args[1]) && args.length >= 3) {
            data = plugin.getLogService().queryByTypeNewestFirst(args[2]);
            exportedScope = "type:" + args[2];
        } else {
            plugin.getMessages().send(sender, "admin.logs.export-usage");
            return true;
        }
        File out = new File(plugin.getDataFolder(), "havoc-logs-export-" + System.currentTimeMillis() + ".log");
        int written = plugin.getLogService().exportToFile(out, data);
        if (written < 0) {
            plugin.getMessages().send(sender, "admin.logs.export-failed");
        } else {
            java.util.Map<String, String> vars = MessageVars.create()
                    .put(MessageKeys.FILE, out.getName())
                    .put(MessageKeys.COUNT, written)
                    .put(MessageKeys.SCOPE, exportedScope)
                    .build();
            plugin.getMessages().send(sender, "admin.logs.export-success", vars);
        }
        return true;
    }

    private AdminLogsParsedFilter parseCombinedLogsFilter(String[] args, int startIndex) {
        AdminLogsParsedFilter f = new AdminLogsParsedFilter();
        int i = startIndex;
        while (i < args.length) {
            String k = args[i].toLowerCase(Locale.ROOT);
            if ("user".equals(k)) {
                if (i + 1 >= args.length) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                f.user = args[i + 1];
                i += 2;
                continue;
            }
            if ("base".equals(k)) {
                if (i + 1 >= args.length) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                f.base = args[i + 1];
                i += 2;
                continue;
            }
            if ("type".equals(k)) {
                if (i + 1 >= args.length) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                f.type = args[i + 1];
                i += 2;
                continue;
            }
            if ("date".equals(k)) {
                if (i + 1 >= args.length) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                long[] rel = HavocLogService.parseRelativeRange(args[i + 1]);
                if (rel != null) {
                    f.from = rel[0];
                    f.to = rel[1];
                    i += 2;
                    continue;
                }
                if (i + 2 >= args.length) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                Long from = HavocLogService.parseDateStartEpochMs(args[i + 1]);
                Long to = HavocLogService.parseDateEndEpochMs(args[i + 2]);
                if (from == null || to == null) {
                    f.fail("admin.logs.invalid-date");
                    return f;
                }
                f.from = from;
                f.to = to;
                i += 3;
                continue;
            }
            if ("page".equals(k)) {
                if (i + 1 >= args.length || !CommandUtil.isInt(args[i + 1])) {
                    f.fail("admin.logs.usage");
                    return f;
                }
                f.page = Integer.parseInt(args[i + 1]);
                i += 2;
                continue;
            }
            if (CommandUtil.isInt(k)) {
                f.page = Integer.parseInt(k);
                i += 1;
                continue;
            }
            f.fail("admin.logs.usage");
            return f;
        }
        List<String> s = new ArrayList<String>();
        if (!f.user.isEmpty()) {
            s.add("user:" + f.user);
        }
        if (!f.base.isEmpty()) {
            s.add("base:" + f.base);
        }
        if (!f.type.isEmpty()) {
            s.add("type:" + f.type);
        }
        if (f.from != null && f.to != null) {
            s.add("date:custom");
        }
        f.scope = s.isEmpty() ? "all" : joinScopes(s);
        f.ok = true;
        return f;
    }

    private static String joinScopes(List<String> scopes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scopes.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(scopes.get(i));
        }
        return sb.toString();
    }

}
