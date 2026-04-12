package com.kartersanamo.havoc.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopConfig {

    public static final class DisplayItem {
        public final int slot;
        public final Material material;
        public final short data;
        public final int amount;
        public final String displayName;
        public final List<String> lore;

        public DisplayItem(int slot, Material material, short data, int amount, String displayName, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.data = data;
            this.amount = amount;
            this.displayName = displayName;
            this.lore = lore;
        }
    }

    private final JavaPlugin plugin;
    private String title = ChatColor.DARK_GREEN + "Havoc Salvage Shop";
    private int rows = 3;
    private boolean fillEmptySlots = true;
    private boolean closeOnPurchase = false;
    private boolean refreshAfterPurchase = true;
    private String purchaseSuccessMessage = "&aPurchased &f{amount}x {name} &afor &e{price} Salvage&a.";
    private String purchaseFailMessage = "&cYou need &e{price}&c Salvage (you have &e{balance}&c).";
    private DisplayItem fillerItem;
    private DisplayItem balanceItem;
    private final List<ShopItem> items = new ArrayList<ShopItem>();

    public ShopConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        title = color(y.getString("title", "&2Havoc Salvage Shop"));
        rows = clampRows(y.getInt("rows", 3));
        fillEmptySlots = y.getBoolean("fill-empty-slots", true);
        closeOnPurchase = y.getBoolean("close-on-purchase", false);
        refreshAfterPurchase = y.getBoolean("refresh-after-purchase", true);
        purchaseSuccessMessage = y.getString("messages.purchase-success", purchaseSuccessMessage);
        purchaseFailMessage = y.getString("messages.insufficient-funds", purchaseFailMessage);
        fillerItem = parseDisplayItem(y.getConfigurationSection("filler-item"), -1,
                Material.STAINED_GLASS_PANE, 7, 1, " ");
        balanceItem = parseDisplayItem(y.getConfigurationSection("balance-item"), 4,
                Material.NETHER_STAR, 0, 1, "&eYour Salvage");

        items.clear();
        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection i = sec.getConfigurationSection(key);
                if (i == null) {
                    continue;
                }
                int slot = i.getInt("slot", 0);
                Material mat = parseMaterial(i.getString("material"), Material.STONE, "items." + key + ".material");
                short data = (short) i.getInt("data", 0);
                int amount = clampAmount(i.getInt("amount", 1));
                int price = Math.max(0, i.getInt("price", 0));
                String name = color(i.getString("display-name", "&f" + mat.name()));
                List<String> lore = colorList(i.getStringList("lore"));
                items.add(new ShopItem(slot, mat, data, amount, price, name, lore));
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public int getSize() {
        return rows * 9;
    }

    public boolean isFillEmptySlots() {
        return fillEmptySlots;
    }

    public boolean isCloseOnPurchase() {
        return closeOnPurchase;
    }

    public boolean isRefreshAfterPurchase() {
        return refreshAfterPurchase;
    }

    public String getPurchaseSuccessMessage() {
        return purchaseSuccessMessage;
    }

    public String getPurchaseFailMessage() {
        return purchaseFailMessage;
    }

    public DisplayItem getFillerItem() {
        return fillerItem;
    }

    public DisplayItem getBalanceItem() {
        return balanceItem;
    }

    public List<ShopItem> getItems() {
        return items;
    }

    public ShopItem findBySlot(int slot) {
        for (ShopItem item : items) {
            if (item.getSlot() == slot) {
                return item;
            }
        }
        return null;
    }

    private DisplayItem parseDisplayItem(ConfigurationSection sec, int defSlot, Material defMat, int defData, int defAmount, String defName) {
        if (sec == null) {
            return null;
        }
        if (!sec.getBoolean("enabled", true)) {
            return null;
        }
        int slot = sec.getInt("slot", defSlot);
        Material mat = parseMaterial(sec.getString("material"), defMat, sec.getCurrentPath() + ".material");
        short data = (short) sec.getInt("data", defData);
        int amount = clampAmount(sec.getInt("amount", defAmount));
        String name = color(sec.getString("display-name", defName));
        List<String> lore = colorList(sec.getStringList("lore"));
        return new DisplayItem(slot, mat, data, amount, name, lore);
    }

    private Material parseMaterial(String raw, Material def, String path) {
        if (raw == null || raw.trim().isEmpty()) {
            return def;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in shop.yml at " + path + ": " + raw + " (using " + def.name() + ")");
            return def;
        }
    }

    private static int clampRows(int r) {
        if (r < 1) {
            return 1;
        }
        if (r > 6) {
            return 6;
        }
        return r;
    }

    private static int clampAmount(int a) {
        if (a < 1) {
            return 1;
        }
        if (a > 64) {
            return 64;
        }
        return a;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static List<String> colorList(List<String> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>(src.size());
        for (String s : src) {
            out.add(color(s));
        }
        return out;
    }
}
