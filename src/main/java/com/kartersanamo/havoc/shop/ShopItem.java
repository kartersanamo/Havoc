package com.kartersanamo.havoc.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
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

    public ShopItem(int slot, Material material, short data, int amount, int price, String displayName, List<String> lore) {
        this.slot = slot;
        this.material = material;
        this.data = data;
        this.amount = amount;
        this.price = price;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.<String>emptyList() : lore;
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

    public ItemStack createDisplayStack(int balance) {
        ItemStack stack = new ItemStack(material, amount, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> outLore = new ArrayList<String>();
            for (String line : lore) {
                outLore.add(replacePlaceholders(line, balance));
            }
            outLore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + price + ChatColor.YELLOW + " Salvage");
            outLore.add(ChatColor.GRAY + "Your Balance: "
                    + (balance >= price ? ChatColor.GREEN : ChatColor.RED) + balance);
            outLore.add(balance >= price
                    ? ChatColor.GREEN + "Click to purchase."
                    : ChatColor.RED + "You cannot afford this.");
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
