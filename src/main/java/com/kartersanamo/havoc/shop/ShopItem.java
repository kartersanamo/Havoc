package com.kartersanamo.havoc.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShopItem {

    private final int slot;
    private final Material material;
    private final byte data;
    private final int amount;
    private final int price;
    private final String displayName;

    public ShopItem(int slot, Material material, byte data, int amount, int price, String displayName) {
        this.slot = slot;
        this.material = material;
        this.data = data;
        this.amount = amount;
        this.price = price;
        this.displayName = displayName;
    }

    public int getSlot() {
        return slot;
    }

    public int getPrice() {
        return price;
    }

    public ItemStack createDisplayStack() {
        ItemStack stack = new ItemStack(material, amount, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createBoughtStack() {
        return new ItemStack(material, amount, data);
    }
}
