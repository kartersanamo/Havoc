package com.kartersanamo.havoc.message;

import org.bukkit.ChatColor;

public final class LeadCoordinateFormat {

    private LeadCoordinateFormat() {
    }

    /**
     * Obfuscates the first digit of a coordinate using Minecraft's {@code §k} formatting
     * so players get a hint without the full value (e.g. {@code §k1§71234}).
     */
    public static String obfuscateFirstDigit(int coordinate) {
        String raw = String.valueOf(coordinate);
        int digitIndex = 0;
        while (digitIndex < raw.length() && !Character.isDigit(raw.charAt(digitIndex))) {
            digitIndex++;
        }
        if (digitIndex >= raw.length()) {
            return raw;
        }
        String prefix = raw.substring(0, digitIndex);
        String firstDigit = String.valueOf(raw.charAt(digitIndex));
        String rest = raw.substring(digitIndex + 1);
        return prefix + ChatColor.MAGIC + firstDigit + ChatColor.GRAY + rest;
    }
}
