package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class SalvageBalanceSubcommand implements HavocSubcommand {

    private final Havoc plugin;

    public SalvageBalanceSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "salvage";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.SALVAGE_BALANCE)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        int bal = plugin.getSalvageStore().get(((Player) sender).getUniqueId());
        plugin.getMessages().send(sender, "command.salvage-balance",
                MessageVars.create().put(MessageKeys.BALANCE, bal).build());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
