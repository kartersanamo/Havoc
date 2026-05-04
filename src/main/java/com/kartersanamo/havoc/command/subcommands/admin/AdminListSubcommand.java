package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AdminListSubcommand implements AdminSubcommand {

    private final Havoc plugin;

    public AdminListSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_LIST;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            if (!sender.hasPermission(PermissionNodes.ADMIN_LIST_GUI)) {
                plugin.getMessages().send(sender, "command.no-permission");
                return true;
            }
            plugin.getBaseAdminGui().openList((Player) sender);
            return true;
        }
        if (!sender.hasPermission(PermissionNodes.ADMIN_LIST_CONSOLE)) {
            plugin.getMessages().send(sender, "command.no-permission");
            return true;
        }
        List<ActiveHavocBase> bases = plugin.getBaseService().listAllBasesSorted();
        if (bases.isEmpty()) {
            plugin.getMessages().send(sender, "admin.list.empty");
            return true;
        }
        plugin.getMessages().send(sender, "admin.list.header",
                MessageVars.create().put(MessageKeys.COUNT, bases.size()).build());
        for (ActiveHavocBase b : bases) {
            String shortId = b.id.toString().substring(0, 8);
            java.util.Map<String, String> vars = MessageVars.create()
                    .put(MessageKeys.DIFFICULTY, b.difficulty.name())
                    .put(MessageKeys.STATE, b.state.name())
                    .put(MessageKeys.WORLD, b.worldName)
                    .put(MessageKeys.X, b.obsidianCenterX)
                    .put(MessageKeys.Y, b.obsidianCenterY)
                    .put(MessageKeys.Z, b.obsidianCenterZ)
                    .put(MessageKeys.ID, shortId)
                    .put(MessageKeys.CLAIMS, b.claimedChunks.size())
                    .build();
            plugin.getMessages().send(sender, "admin.list.line", vars);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<String>();
    }
}
