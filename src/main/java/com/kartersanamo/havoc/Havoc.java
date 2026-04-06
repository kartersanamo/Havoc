package com.kartersanamo.havoc;

import com.kartersanamo.havoc.base.BaseService;
import com.kartersanamo.havoc.command.HavocCommandExecutor;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.listener.HavocListener;
import com.kartersanamo.havoc.shop.SalvageShop;
import com.kartersanamo.havoc.storage.ProgressionStore;
import com.kartersanamo.havoc.storage.SalvageStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Havoc extends JavaPlugin {

    private static Havoc instance;
    private HavocConfig havocConfig;
    private FactionsBridge factionsBridge;
    private BaseService baseService;
    private SalvageStore salvageStore;
    private ProgressionStore progressionStore;
    private SalvageShop salvageShop;

    public static Havoc getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File schemDir = new File(getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            schemDir.mkdirs();
        }
        saveDefaultConfig();
        havocConfig = new HavocConfig(this);
        havocConfig.reload();
        salvageStore = new SalvageStore(this);
        salvageStore.load();
        progressionStore = new ProgressionStore(this);
        progressionStore.load();
        factionsBridge = new FactionsBridge(this);
        if (!factionsBridge.init()) {
            getLogger().severe("Disabling Havoc: Factions API not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        baseService = new BaseService(this);
        baseService.start();
        salvageShop = new SalvageShop(this);
        Bukkit.getPluginManager().registerEvents(new HavocListener(this), this);
        if (getCommand("havoc") != null) {
            HavocCommandExecutor ex = new HavocCommandExecutor(this);
            getCommand("havoc").setExecutor(ex);
            getCommand("havoc").setTabCompleter(ex);
        }
        applyWorldBorder();
        HavocDebug.announce(this, "Havoc enabled — world \"" + havocConfig.getWorldName() + "\" chunk centers (8,8), WE offset mode + optional bedrock snap (see config.yml).");
    }

    public void applyWorldBorder() {
        World w = Bukkit.getWorld(havocConfig.getWorldName());
        if (w == null) {
            getLogger().warning("World \"" + havocConfig.getWorldName() + "\" not loaded; skipping world border.");
            return;
        }
        WorldBorder b = w.getWorldBorder();
        b.setCenter(0, 0);
        b.setSize(havocConfig.getBorderHalfSize() * 2.0);
    }

    @Override
    public void onDisable() {
        if (baseService != null) {
            baseService.shutdownFull();
        }
        if (salvageStore != null) {
            salvageStore.save();
        }
        if (progressionStore != null) {
            progressionStore.save();
        }
        instance = null;
    }

    public HavocConfig getHavocConfig() {
        return havocConfig;
    }

    public FactionsBridge getFactionsBridge() {
        return factionsBridge;
    }

    public BaseService getBaseService() {
        return baseService;
    }

    public SalvageStore getSalvageStore() {
        return salvageStore;
    }

    public ProgressionStore getProgressionStore() {
        return progressionStore;
    }

    public SalvageShop getSalvageShop() {
        return salvageShop;
    }
}
