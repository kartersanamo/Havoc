package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AdminBasesSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminBasesSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "bases";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_BASES;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        plugin.getBaseTemplateEditorGui().openDifficultyList((Player) sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<String>();
    }
}
