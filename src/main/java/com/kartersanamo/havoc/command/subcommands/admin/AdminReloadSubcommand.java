package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class AdminReloadSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminReloadSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_RELOAD;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reloadAllConfigsAndStorage();
        plugin.getMessages().send(sender, "admin.reload.success");
        plugin.getLogService().log("ADMIN_RELOAD", sender.getName(), "", null, "reload configs");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<String>();
    }
}
