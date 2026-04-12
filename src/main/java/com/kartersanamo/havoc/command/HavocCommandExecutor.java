package com.kartersanamo.havoc.command;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.config.HavocConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HavocCommandExecutor implements CommandExecutor, TabCompleter {

    private final Havoc plugin;

    public HavocCommandExecutor(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/havoc shop " + ChatColor.GRAY + "- open Salvage shop");
            sender.sendMessage(ChatColor.YELLOW + "/havoc salvage " + ChatColor.GRAY + "- balance");
            if (sender.hasPermission("havoc.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "/havoc admin list " + ChatColor.GRAY + "- all bases");
                sender.sendMessage(ChatColor.YELLOW + "/havoc admin spawn <EASY|MEDIUM|HARD> " + ChatColor.GRAY + "- force one base");
                sender.sendMessage(ChatColor.YELLOW + "/havoc admin reload " + ChatColor.GRAY + "- reload config");
            }
            return true;
        }
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if ("shop".equals(a0)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            plugin.getSalvageShop().open((Player) sender);
            return true;
        }
        if ("salvage".equals(a0)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            int bal = plugin.getSalvageStore().get(((Player) sender).getUniqueId());
            sender.sendMessage(ChatColor.GOLD + "Salvage: " + bal);
            return true;
        }
        if ("admin".equals(a0)) {
            if (!sender.hasPermission("havoc.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.GRAY + "Try: /havoc admin list | /havoc admin reload | /havoc admin spawn <tier>");
                return true;
            }
            String a1 = args[1].toLowerCase(Locale.ROOT);
            if ("list".equals(a1)) {
                List<ActiveHavocBase> bases = plugin.getBaseService().listAllBasesSorted();
                if (bases.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No Havoc bases loaded.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Havoc bases (" + bases.size() + "):");
                for (ActiveHavocBase b : bases) {
                    String shortId = b.id.toString().substring(0, 8);
                    sender.sendMessage(ChatColor.WHITE + "- " + ChatColor.AQUA + b.difficulty
                            + ChatColor.GRAY + " (" + b.state + ") "
                            + ChatColor.WHITE + b.worldName + " "
                            + ChatColor.YELLOW + b.obsidianCenterX + ", " + b.obsidianCenterY + ", " + b.obsidianCenterZ
                            + ChatColor.DARK_GRAY + " [~" + shortId + "] "
                            + ChatColor.DARK_GRAY + "(" + b.claimedChunks.size() + " chunks)");
                }
                return true;
            }
            if ("reload".equals(a1)) {
                HavocConfig c = plugin.getHavocConfig();
                c.reload();
                plugin.getSalvageShop().reload();
                plugin.getSalvageStore().load();
                plugin.getProgressionStore().load();
                plugin.applyWorldBorder();
                sender.sendMessage(ChatColor.GREEN + "Havoc config and shop.yml reloaded.");
                return true;
            }
            if ("spawn".equals(a1)) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GRAY + "Usage: /havoc admin spawn <EASY|MEDIUM|HARD>");
                    return true;
                }
                BaseDifficulty d;
                try {
                    d = BaseDifficulty.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid tier.");
                    return true;
                }
                boolean ok = plugin.getBaseService().trySpawnOne(d);
                sender.sendMessage(ok ? ChatColor.GREEN + "Spawned one " + d + " base." : ChatColor.RED + "Spawn failed (see console).");
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.asList("shop", "salvage", "admin"), args[0]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("list", "reload", "spawn"), args[1]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1]) && sender.hasPermission("havoc.admin")) {
            return partial(Arrays.asList("EASY", "MEDIUM", "HARD"), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> partial(List<String> opts, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
