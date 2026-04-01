package com.kartersanamo.havoc.shop;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.storage.SalvageStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SalvageShop {

    private final Havoc plugin;

    public SalvageShop(Havoc plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        HavocConfig cfg = plugin.getHavocConfig();
        int rows = cfg.getShopRows();
        int size = rows * 9;
        Inventory inv = Bukkit.createInventory(new ShopHolder(), size, ChatColor.DARK_GREEN + "Salvage Shop");
        for (ShopItem item : cfg.getShopItems()) {
            if (item.getSlot() >= 0 && item.getSlot() < size) {
                inv.setItem(item.getSlot(), item.createDisplayStack());
            }
        }
        player.openInventory(inv);
    }

    public boolean isShopInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ShopHolder;
    }

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= player.getOpenInventory().getTopInventory().getSize()) {
            return;
        }
        HavocConfig cfg = plugin.getHavocConfig();
        for (ShopItem item : cfg.getShopItems()) {
            if (item.getSlot() == rawSlot) {
                SalvageStore store = plugin.getSalvageStore();
                int bal = store.get(player.getUniqueId());
                if (bal < item.getPrice()) {
                    player.sendMessage(ChatColor.RED + "Not enough Salvage.");
                    return;
                }
                store.add(player.getUniqueId(), -item.getPrice());
                player.getInventory().addItem(item.createBoughtStack());
                player.sendMessage(ChatColor.GREEN + "Purchased for " + item.getPrice() + " Salvage.");
                return;
            }
        }
    }

    private static final class ShopHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
