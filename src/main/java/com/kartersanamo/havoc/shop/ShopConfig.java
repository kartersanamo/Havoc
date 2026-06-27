package com.kartersanamo.havoc.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopConfig {

    private static final int BUNDLED_CONFIG_VERSION = 2;

    private final JavaPlugin plugin;
    private String title = "Havoc Shop";
    private int rows = 3;
    private boolean fillEmptySlots = true;
    private boolean closeOnPurchase = false;
    private boolean refreshAfterPurchase = true;
    private String priceLoreLine = "&7Price: &6{price} &eSalvage";
    private String balanceLoreLine = "&7Your Balance: &7{balance}";
    private String canAffordLoreLine = "&aClick to purchase.";
    private String cannotAffordLoreLine = "&cYou cannot afford this.";
    private ShopDisplayItem fillerItem;
    private ShopDisplayItem balanceItem;
    private final List<ShopItem> items = new ArrayList<ShopItem>();

    public ShopConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration defaults = loadDefaultShopConfiguration();
        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration y = loadOrCreateUserConfiguration(file, defaults);
        migrateShopConfiguration(file, y, defaults);

        title = plainTitle(y.getString("title", defaults == null ? "Havoc Shop" : defaults.getString("title", "Havoc Shop")));
        rows = clampRows(y.getInt("rows", defaults == null ? 3 : defaults.getInt("rows", 3)));
        fillEmptySlots = y.getBoolean("fill-empty-slots", defaults == null || defaults.getBoolean("fill-empty-slots"));
        closeOnPurchase = y.getBoolean("close-on-purchase", defaults != null && defaults.getBoolean("close-on-purchase"));
        refreshAfterPurchase = y.getBoolean("refresh-after-purchase", defaults == null || defaults.getBoolean("refresh-after-purchase"));
        priceLoreLine = y.getString("item-extra-lore.price-line", priceLoreLine);
        balanceLoreLine = y.getString("item-extra-lore.balance-line", balanceLoreLine);
        canAffordLoreLine = y.getString("item-extra-lore.can-afford-line", canAffordLoreLine);
        cannotAffordLoreLine = y.getString("item-extra-lore.cannot-afford-line", cannotAffordLoreLine);
        fillerItem = parseDisplayItem(y.getConfigurationSection("filler-item"), -1,
                Material.STAINED_GLASS_PANE, 7, 1, " ");
        balanceItem = parseDisplayItem(y.getConfigurationSection("balance-item"), 4,
                Material.NETHER_STAR, 0, 1, "&eYour Salvage");

        items.clear();
        items.addAll(parseItems(y.getConfigurationSection("items")));
        if (items.isEmpty() && defaults != null) {
            plugin.getLogger().warning("No shop items loaded from plugins/Havoc/shop.yml; using bundled defaults.");
            items.addAll(parseItems(defaults.getConfigurationSection("items")));
        }
        rows = ensureRowsFitItems(rows, items, balanceItem);

        if (items.isEmpty()) {
            plugin.getLogger().warning("No shop items loaded. Check plugins/Havoc/shop.yml for YAML errors.");
        } else {
            plugin.getLogger().info("Shop loaded: title=\"" + title + "\", rows=" + rows + ", items=" + items.size()
                    + " (from plugins/Havoc/shop.yml).");
        }
    }

    private YamlConfiguration loadOrCreateUserConfiguration(File file, YamlConfiguration defaults) {
        if (!file.exists()) {
            if (defaults != null) {
                try {
                    defaults.save(file);
                    plugin.getLogger().info("Created plugins/Havoc/shop.yml from bundled defaults.");
                    return defaults;
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create shop.yml: " + e.getMessage());
                }
            }
            return new YamlConfiguration();
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        return loaded;
    }

    private void migrateShopConfiguration(File file, YamlConfiguration user, YamlConfiguration defaults) {
        if (defaults == null || !file.exists()) {
            return;
        }
        int bundledVersion = defaults.getInt("config-version", BUNDLED_CONFIG_VERSION);
        int userVersion = user.getInt("config-version", 0);
        if (userVersion >= bundledVersion) {
            return;
        }

        copyIfPresent(defaults, user, "title");
        copyIfPresent(defaults, user, "rows");
        copyIfPresent(defaults, user, "fill-empty-slots");
        copyIfPresent(defaults, user, "close-on-purchase");
        copyIfPresent(defaults, user, "refresh-after-purchase");
        copyIfPresent(defaults, user, "item-extra-lore");
        copyIfPresent(defaults, user, "filler-item");
        copyIfPresent(defaults, user, "balance-item");
        if (!hasValidItemSections(user)) {
            copyIfPresent(defaults, user, "items");
        }
        user.set("config-version", bundledVersion);
        try {
            user.save(file);
            plugin.getLogger().info("Updated plugins/Havoc/shop.yml to config version " + bundledVersion + ".");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save migrated shop.yml: " + e.getMessage());
        }
    }

    private static boolean hasValidItemSections(YamlConfiguration config) {
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            return false;
        }
        for (String key : itemsSection.getKeys(false)) {
            if (itemsSection.getConfigurationSection(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static void copyIfPresent(YamlConfiguration from, YamlConfiguration to, String path) {
        if (from.contains(path)) {
            to.set(path, from.get(path));
        }
    }

    private List<ShopItem> parseItems(ConfigurationSection sec) {
        if (sec == null) {
            return Collections.emptyList();
        }
        List<ShopItem> parsed = new ArrayList<ShopItem>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection i = sec.getConfigurationSection(key);
            if (i == null) {
                plugin.getLogger().warning("Invalid shop item entry (fix YAML syntax): items." + key);
                continue;
            }
            int slot = i.getInt("slot", 0);
            Material mat = parseMaterial(i.getString("material"), Material.STONE, "items." + key + ".material");
            short data = (short) i.getInt("data", 0);
            int amount = clampAmount(i.getInt("amount", 1));
            int price = Math.max(0, i.getInt("price", 0));
            String name = color(i.getString("display-name", "&7" + mat.name()));
            List<String> lore = colorList(i.getStringList("lore"));
            List<String> commands = i.getStringList("commands");
            if (commands == null) {
                commands = Collections.emptyList();
            }
            boolean giveItem = i.contains("give-item")
                    ? i.getBoolean("give-item")
                    : commands.isEmpty();
            parsed.add(new ShopItem(slot, mat, data, amount, price, name, lore, commands, giveItem));
        }
        return parsed;
    }

    private static int ensureRowsFitItems(int configuredRows, List<ShopItem> parsedItems, ShopDisplayItem balance) {
        int maxSlot = -1;
        for (ShopItem item : parsedItems) {
            maxSlot = Math.max(maxSlot, item.getSlot());
        }
        if (balance != null && balance.slot >= 0) {
            maxSlot = Math.max(maxSlot, balance.slot);
        }
        if (maxSlot < 0) {
            return configuredRows;
        }
        int requiredRows = clampRows((maxSlot / 9) + 1);
        if (requiredRows > configuredRows) {
            return requiredRows;
        }
        return configuredRows;
    }

    private YamlConfiguration loadDefaultShopConfiguration() {
        InputStream in = plugin.getResource("shop.yml");
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static String plainTitle(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Havoc Shop";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
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
