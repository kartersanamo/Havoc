package com.kartersanamo.havoc.command.subcommands;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface HavocSubcommand {
    String name();
    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);
}
