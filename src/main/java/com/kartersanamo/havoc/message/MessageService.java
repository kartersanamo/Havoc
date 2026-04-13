package com.kartersanamo.havoc.message;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender to, String key) {
        send(to, key, null);
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        String raw = cfg.getString(key);
        if (raw == null) {
            to.sendMessage(ChatColor.RED + "Missing message key: " + key);
            return;
        }
        to.sendMessage(apply(raw, placeholders));
    }

    public void sendList(CommandSender to, String key, Map<String, String> placeholders) {
        List<String> lines = cfg.getStringList(key);
        if (lines == null || lines.isEmpty()) {
            String single = cfg.getString(key);
            if (single != null) {
                to.sendMessage(apply(single, placeholders));
            }
            return;
        }
        for (String line : lines) {
            to.sendMessage(apply(line, placeholders));
        }
    }

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = cfg.getString(key);
        if (raw == null) {
            return ChatColor.RED + "Missing message key: " + key;
        }
        return apply(raw, placeholders);
    }

    public List<String> getList(String key, Map<String, String> placeholders) {
        List<String> lines = cfg.getStringList(key);
        if (lines == null || lines.isEmpty()) {
            return Collections.singletonList(get(key, placeholders));
        }
        List<String> out = new ArrayList<String>(lines.size());
        for (String line : lines) {
            out.add(apply(line, placeholders));
        }
        return out;
    }

    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        // 1.8 has no native Spigot API actionbar without NMS; fallback to chat for portability.
        send(player, key, placeholders);
    }

    private String apply(String raw, Map<String, String> placeholders) {
        String out = ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String v = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{" + e.getKey() + "}", v);
            }
        }
        return out;
    }
}
