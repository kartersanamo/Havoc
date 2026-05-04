package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.kartersanamo.havoc.command.subcommands.CommandUtil.partial;

public final class AdminSalvageSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminSalvageSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "salvage";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_SALVAGE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().send(sender, "admin.salvage.usage");
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            plugin.getMessages().send(sender, "admin.salvage.player-not-found",
                    MessageVars.one(MessageKeys.PLAYER, args[2]));
            return true;
        }
        if ("show".equals(mode)) {
            if (!sender.hasPermission(PermissionNodes.ADMIN_SALVAGE_SHOW)) {
                plugin.getMessages().send(sender, "command.no-permission");
                return true;
            }
            int bal = plugin.getSalvageStore().get(target.getUniqueId());
            java.util.Map<String, String> vars = MessageVars.create()
                    .put(MessageKeys.PLAYER, target.getName() == null ? args[2] : target.getName())
                    .put(MessageKeys.BALANCE, bal)
                    .build();
            plugin.getMessages().send(sender, "admin.salvage.show", vars);
            return true;
        }
        if (!"add".equals(mode) && !"set".equals(mode)) {
            plugin.getMessages().send(sender, "admin.salvage.invalid-action");
            return true;
        }
        if ("add".equals(mode) && !sender.hasPermission(PermissionNodes.ADMIN_SALVAGE_ADD)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if ("set".equals(mode) && !sender.hasPermission(PermissionNodes.ADMIN_SALVAGE_SET)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length < 4) {
            plugin.getMessages().send(sender, "admin.salvage.amount-required");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
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
        java.util.Map<String, String> vars = MessageVars.create()
                .put(MessageKeys.PLAYER, target.getName() == null ? args[2] : target.getName())
                .put(MessageKeys.AMOUNT, amount)
                .put(MessageKeys.BALANCE, bal)
                .build();
        plugin.getMessages().send(sender, "add".equals(mode) ? "admin.salvage.add-success" : "admin.salvage.set-success", vars);
        plugin.getLogService().log("ADMIN_SALVAGE_" + mode.toUpperCase(Locale.ROOT),
                sender.getName(), "", null,
                "target=" + vars.get("player") + ", amount=" + amount + ", balance=" + bal);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return partial(Arrays.asList("add", "set", "show"), args[1]);
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partial(names, args[2]);
        }
        return java.util.Collections.emptyList();
    }
}
