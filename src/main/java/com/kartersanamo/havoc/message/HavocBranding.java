package com.kartersanamo.havoc.message;

import org.bukkit.ChatColor;

public final class HavocBranding {

    private static final String DEFAULT_CHAT_PREFIX = "&4&lHavoc &8&l»&7 ";
    private static volatile String chatPrefix = color(DEFAULT_CHAT_PREFIX);

    private HavocBranding() {
    }

    public static void reload(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.trim().isEmpty()) {
            chatPrefix = color(DEFAULT_CHAT_PREFIX);
            return;
        }
        chatPrefix = color(rawPrefix);
    }

    public static String chatPrefix() {
        return chatPrefix;
    }

    public static String formatChat(String body) {
        String text = body == null ? "" : body;
        if (text.startsWith(chatPrefix)) {
            return text;
        }
        return chatPrefix + text;
    }

    public static String formatGuiTitle(String suffix) {
        if (suffix == null || suffix.trim().isEmpty()) {
            return "Havoc";
        }
        return "Havoc " + suffix.trim();
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
