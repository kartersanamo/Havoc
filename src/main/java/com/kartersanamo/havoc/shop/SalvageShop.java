package com.kartersanamo.havoc.shop;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import com.kartersanamo.havoc.permission.PermissionNodes;
import com.kartersanamo.havoc.storage.SalvageStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SalvageShop {

    private final Havoc plugin;
    private final ShopConfig config;

    public SalvageShop(Havoc plugin) {
        this.plugin = plugin;
        this.config = new ShopConfig(plugin);
        reload();
    }

    public void reload() {
        config.reload();
    }

    public void open(Player player) {
        int size = config.getSize();
        int balance = plugin.getSalvageStore().get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(new SalvageShopHolder(), size, config.getTitle());
        if (config.isFillEmptySlots()) {
            ItemStack filler = createStaticDisplay(config.getFillerItem(), balance);
            if (filler != null) {
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, filler.clone());
                }
            }
        }
        for (ShopItem item : config.getItems()) {
            if (item.getSlot() >= 0 && item.getSlot() < size) {
                inv.setItem(item.getSlot(), item.createDisplayStack(balance,
                        config.getPriceLoreLine(),
                        config.getBalanceLoreLine(),
                        config.getCanAffordLoreLine(),
                        config.getCannotAffordLoreLine()));
            }
        }
        ItemStack balanceDisplay = createStaticDisplay(config.getBalanceItem(), balance);
        ShopDisplayItem bd = config.getBalanceItem();
        if (balanceDisplay != null && bd != null && bd.slot >= 0 && bd.slot < size) {
            inv.setItem(bd.slot, balanceDisplay);
        }
        player.openInventory(inv);
    }

    public boolean isShopInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof SalvageShopHolder;
    }

    public void handleClick(Player player, int rawSlot) {
        if (!player.hasPermission(PermissionNodes.SHOP_PURCHASE)) {
            plugin.getMessages().send(player, "command.no-permission");
            return;
        }
        if (rawSlot < 0 || rawSlot >= player.getOpenInventory().getTopInventory().getSize()) {
            return;
        }
        ShopItem item = config.findBySlot(rawSlot);
        if (item == null) {
            return;
        }
        SalvageStore store = plugin.getSalvageStore();
        int bal = store.get(player.getUniqueId());
        if (bal < item.getPrice()) {
            plugin.getMessages().send(player, "shop.purchase.insufficient-funds", vars(item, bal));
            return;
        }
        store.add(player.getUniqueId(), -item.getPrice());
        plugin.getPlayerStatsStore().addShopPurchase(player.getUniqueId(), item.getPrice());
        plugin.getPlayerStatsStore().saveAsync();
        store.saveAsync();
        player.getInventory().addItem(item.createBoughtStack());
        int newBal = store.get(player.getUniqueId());
        plugin.getMessages().send(player, "shop.purchase.success", vars(item, newBal));
        if (config.isCloseOnPurchase()) {
            player.closeInventory();
            return;
        }
        if (config.isRefreshAfterPurchase()) {
            open(player);
        }
    }

    private ItemStack createStaticDisplay(ShopDisplayItem spec, int balance) {
        if (spec == null) {
            return null;
        }
        Material material = spec.material == null ? Material.PAPER : spec.material;
        ItemStack stack = new ItemStack(material, Math.max(1, spec.amount), spec.data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(replace(spec.displayName, null, balance));
            if (spec.lore != null && !spec.lore.isEmpty()) {
                List<String> lore = new ArrayList<String>(spec.lore.size());
                for (String line : spec.lore) {
                    lore.add(replace(line, null, balance));
                }
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String replace(String raw, ShopItem item, int balance) {
        String out = raw == null ? "" : raw;
        out = out.replace("{balance}", String.valueOf(balance));
        if (item != null) {
            out = out.replace("{price}", String.valueOf(item.getPrice()));
            out = out.replace("{amount}", String.valueOf(item.getAmount()));
            out = out.replace("{name}", item.getDisplayName());
        } else {
            out = out.replace("{price}", "0");
            out = out.replace("{amount}", "0");
            out = out.replace("{name}", "");
        }
        return out;
    }

    private Map<String, String> vars(ShopItem item, int balance) {
        return MessageVars.create()
                .put("price", item.getPrice())
                .put(MessageKeys.AMOUNT, item.getAmount())
                .put("name", item.getDisplayName())
                .put(MessageKeys.BALANCE, balance)
                .build();
    }

}
