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

    private final JavaPlugin plugin;
    private String title = ChatColor.DARK_GREEN + "Havoc Salvage Shop";
    private int rows = 3;
    private boolean fillEmptySlots = true;
    private boolean closeOnPurchase = false;
    private boolean refreshAfterPurchase = true;
    private String priceLoreLine = "&7Price: &6{price} &eSalvage";
    private String balanceLoreLine = "&7Your Balance: &f{balance}";
    private String canAffordLoreLine = "&aClick to purchase.";
    private String cannotAffordLoreLine = "&cYou cannot afford this.";
    private ShopDisplayItem fillerItem;
    private ShopDisplayItem balanceItem;
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
        priceLoreLine = y.getString("item-extra-lore.price-line", priceLoreLine);
        balanceLoreLine = y.getString("item-extra-lore.balance-line", balanceLoreLine);
        canAffordLoreLine = y.getString("item-extra-lore.can-afford-line", canAffordLoreLine);
        cannotAffordLoreLine = y.getString("item-extra-lore.cannot-afford-line", cannotAffordLoreLine);
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

    public String getPriceLoreLine() {
        return priceLoreLine;
    }

    public String getBalanceLoreLine() {
        return balanceLoreLine;
    }

    public String getCanAffordLoreLine() {
        return canAffordLoreLine;
    }

    public String getCannotAffordLoreLine() {
        return cannotAffordLoreLine;
    }

    public ShopDisplayItem getFillerItem() {
        return fillerItem;
    }

    public ShopDisplayItem getBalanceItem() {
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

    private ShopDisplayItem parseDisplayItem(ConfigurationSection sec, int defSlot, Material defMat, int defData, int defAmount, String defName) {
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
        return new ShopDisplayItem(slot, mat, data, amount, name, lore);
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
