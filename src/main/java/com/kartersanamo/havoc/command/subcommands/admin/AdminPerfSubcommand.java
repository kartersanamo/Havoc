package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AdminPerfSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminPerfSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_PERF;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int queue = plugin.getBaseService().getSpawnQueueSize();
        String phase = plugin.getBaseService().getSpawnActivePhase();
        String difficulty = plugin.getBaseService().getSpawnActiveDifficulty();
        double avgMs = plugin.getBaseService().getSpawnWorkerAvgMs();
        long samples = plugin.getBaseService().getSpawnWorkerSamples();
        plugin.getMessages().send(sender, "admin.perf.header");
        plugin.getMessages().send(sender, "admin.perf.queue",
                MessageVars.create().put(MessageKeys.COUNT, queue).build());
        plugin.getMessages().send(sender, "admin.perf.active",
                MessageVars.create()
                        .put("phase", phase)
                        .put(MessageKeys.DIFFICULTY, difficulty)
                        .build());
        plugin.getMessages().send(sender, "admin.perf.spawn-worker",
                MessageVars.create()
                        .put("avg_ms", String.format(Locale.US, "%.3f", avgMs))
                        .put("samples", String.valueOf(samples))
                        .build());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
