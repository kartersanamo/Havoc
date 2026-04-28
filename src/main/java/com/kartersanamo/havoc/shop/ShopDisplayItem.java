package com.kartersanamo.havoc.shop;

import org.bukkit.Material;

import java.util.List;

public final class ShopDisplayItem {
    public final int slot;
    public final Material material;
    public final short data;
    public final int amount;
    public final String displayName;
    public final List<String> lore;

    public ShopDisplayItem(int slot, Material material, short data, int amount, String displayName, List<String> lore) {
        this.slot = slot;
        this.material = material;
        this.data = data;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore;
    }
}
