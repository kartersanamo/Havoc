package com.kartersanamo.havoc.command.subcommands.admin;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface AdminSubcommand {
    String name();
    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);
}
