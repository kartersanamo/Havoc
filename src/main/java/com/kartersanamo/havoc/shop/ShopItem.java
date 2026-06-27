package com.kartersanamo.havoc.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopItem {

    private final int slot;
    private final Material material;
    private final short data;
    private final int amount;
    private final int price;
    private final String displayName;
    private final List<String> lore;
    private final List<String> commands;
    private final boolean giveItem;

    public ShopItem(int slot, Material material, short data, int amount, int price, String displayName,
                    List<String> lore, List<String> commands, boolean giveItem) {
        this.slot = slot;
        this.material = material;
        this.data = data;
        this.amount = amount;
        this.price = price;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.<String>emptyList() : lore;
        this.commands = commands == null ? Collections.<String>emptyList() : commands;
        this.giveItem = giveItem;
    }

    public int getSlot() {
        return slot;
    }

    public int getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean shouldGiveItem() {
        return giveItem;
    }

    public String resolveCommand(String command, Player player) {
        String playerName = player.getName() == null ? "" : player.getName();
        return command
                .replace("{player}", playerName)
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{price}", String.valueOf(price))
                .replace("{amount}", String.valueOf(amount))
                .replace("{name}", ChatColor.stripColor(displayName));
    }

    public ItemStack createDisplayStack(int balance, String priceLine, String balanceLine, String canAffordLine, String cannotAffordLine) {
        ItemStack stack = new ItemStack(material, amount, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> outLore = new ArrayList<String>();
            for (String line : lore) {
                outLore.add(replacePlaceholders(line, balance));
            }
            outLore.add(replacePlaceholders(priceLine, balance));
            outLore.add(replacePlaceholders(balanceLine, balance));
            outLore.add(replacePlaceholders(balance >= price ? canAffordLine : cannotAffordLine, balance));
            for (int i = 0; i < outLore.size(); i++) {
                outLore.set(i, ChatColor.translateAlternateColorCodes('&', outLore.get(i)));
            }
            meta.setLore(outLore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createBoughtStack() {
        return new ItemStack(material, amount, data);
    }

    private String replacePlaceholders(String raw, int balance) {
        return raw
                .replace("{price}", String.valueOf(price))
                .replace("{balance}", String.valueOf(balance))
                .replace("{amount}", String.valueOf(amount))
                .replace("{material}", material.name());
    }
}
