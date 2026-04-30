package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.command.subcommands.CommandUtil;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminSpawnSubcommand implements AdminSubcommand {

    private final Havoc plugin;
    private final ConcurrentHashMap<String, Long> pendingConfirmMs = new ConcurrentHashMap<String, Long>();
    private volatile long lastSpawnActionMs;

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
        HavocConfig cfg = plugin.getHavocConfig();
        long now = System.currentTimeMillis();
        long cooldownMs = cfg.getAdminSpawnCooldownMs();
        long waitMs = cooldownMs - (now - lastSpawnActionMs);
        if (waitMs > 0L) {
            plugin.getMessages().send(sender, "admin.spawn.cooldown",
                    MessageVars.create().put(MessageKeys.SECONDS, String.valueOf(Math.max(1L, (waitMs + 999L) / 1000L))).build());
            return true;
        }
        if (cfg.isAdminSpawnRequireConfirmation()) {
            String key = sender.getName().toLowerCase(Locale.ROOT) + ":" + d.name();
            Long ts = pendingConfirmMs.get(key);
            if (ts == null || now - ts.longValue() > cfg.getAdminSpawnConfirmWindowMs()) {
                pendingConfirmMs.put(key, now);
                plugin.getMessages().send(sender, "admin.spawn.confirm",
                        MessageVars.create()
                                .put(MessageKeys.SECONDS, String.valueOf(Math.max(1L, (cfg.getAdminSpawnConfirmWindowMs() + 999L) / 1000L)))
                                .build());
                return true;
            }
            pendingConfirmMs.remove(key);
        }
        boolean ok = plugin.getBaseService().trySpawnOne(d);
        lastSpawnActionMs = now;
        plugin.getMessages().send(sender, ok ? "admin.spawn.success" : "admin.spawn.failed",
                MessageVars.one(MessageKeys.DIFFICULTY, d.name()));
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
