package com.kartersanamo.havoc.command.subcommands;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ShopSubcommand implements HavocSubcommand {

    private final Havoc plugin;

    public ShopSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "shop";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        plugin.getSalvageShop().open((Player) sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
