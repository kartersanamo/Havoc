package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.audit.HavocLogEntry;
import com.kartersanamo.havoc.command.subcommands.CommandUtil;
import com.kartersanamo.havoc.message.HavocBranding;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdminLogsSubcommand implements AdminSubcommand {

    private static final int PER_PAGE = 10;

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
        int page = 1;
        if (args.length >= 1) {
            if (!CommandUtil.isInt(args[0])) {
                plugin.getMessages().send(sender, "admin.logs.usage");
                return true;
            }
            page = Integer.parseInt(args[0]);
        }

        List<HavocLogEntry> logs = plugin.getLogService().queryAllNewestFirst();
        if (logs.isEmpty()) {
            plugin.getMessages().send(sender, "admin.logs.empty");
            return true;
        }

        int pages = Math.max(1, (logs.size() + PER_PAGE - 1) / PER_PAGE);
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(logs.size(), from + PER_PAGE);
        plugin.getMessages().send(sender, "admin.logs.header", MessageVars.create()
                .put(MessageKeys.PAGE, page)
                .put(MessageKeys.PAGES, pages)
                .put(MessageKeys.COUNT, logs.size())
                .build());
        for (int i = from; i < to; i++) {
            sender.sendMessage(HavocBranding.formatChat(plugin.getLogService().formatForChat(logs.get(i))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtil.partial(Arrays.asList("1", "2", "3"), args[0]);
        }
        return Collections.emptyList();
    }
}
