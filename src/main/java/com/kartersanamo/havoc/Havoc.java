package com.kartersanamo.havoc;

import com.kartersanamo.havoc.base.BaseService;
import com.kartersanamo.havoc.base.BaseLifecycleEventHandler;
import com.kartersanamo.havoc.admin.BaseAdminGui;
import com.kartersanamo.havoc.audit.HavocLogService;
import com.kartersanamo.havoc.command.HavocCommandExecutor;
import com.kartersanamo.havoc.config.HavocConfig;
import com.kartersanamo.havoc.debug.HavocDebug;
import com.kartersanamo.havoc.faction.FactionsBridge;
import com.kartersanamo.havoc.listener.HavocListener;
import com.kartersanamo.havoc.message.MessageService;
import com.kartersanamo.havoc.event.BaseBreachedEvent;
import com.kartersanamo.havoc.event.BaseRestoredEvent;
import com.kartersanamo.havoc.event.BaseSpawnedEvent;
import com.kartersanamo.havoc.event.EventSubscriber;
import com.kartersanamo.havoc.event.InternalEventBus;
import com.kartersanamo.havoc.shop.SalvageShop;
import com.kartersanamo.havoc.storage.AsyncPersistenceQueue;
import com.kartersanamo.havoc.storage.ProgressionStore;
import com.kartersanamo.havoc.storage.SalvageStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public final class Havoc extends JavaPlugin {

    private static Havoc instance;
    private HavocConfig havocConfig;
    private FactionsBridge factionsBridge;
    private BaseService baseService;
    private BaseAdminGui baseAdminGui;
    private HavocLogService logService;
    private MessageService messages;
    private InternalEventBus eventBus;
    private AsyncPersistenceQueue persistenceQueue;
    private SalvageStore salvageStore;
    private ProgressionStore progressionStore;
    private SalvageShop salvageShop;
    private int progressionResetTaskId = -1;

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
        saveResource("messages.yml", false);
        saveResource("shop.yml", false);

        messages = new MessageService(this);
        eventBus = new InternalEventBus();
        persistenceQueue = new AsyncPersistenceQueue();
        havocConfig = new HavocConfig(this);
        havocConfig.reload();
        logService = new HavocLogService(this);
        logService.configureRetention(havocConfig.getMaxLogLines(), havocConfig.getMaxLogDays(), havocConfig.isArchiveLogsOnRotate());
        logService.load();

        if (Bukkit.getWorld(havocConfig.getWorldName()) == null) {
            getLogger().severe("Havoc: world \"" + havocConfig.getWorldName() + "\" is not loaded — bases cannot spawn until Multiverse (or server.properties) loads that world.");
        }
        salvageStore = new SalvageStore(this, persistenceQueue);
        salvageStore.load();
        progressionStore = new ProgressionStore(this, persistenceQueue);
        progressionStore.load();

        factionsBridge = new FactionsBridge(this);
        if (!factionsBridge.init()) {
            getLogger().severe("Disabling Havoc: Factions API not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        baseService = new BaseService(this, eventBus);
        registerBaseLifecycleSubscribers();
        baseService.start();
        
        baseAdminGui = new BaseAdminGui(this);
        salvageShop = new SalvageShop(this);

        Bukkit.getPluginManager().registerEvents(new HavocListener(this), this);
        if (getCommand("havoc") != null) {
            HavocCommandExecutor ex = new HavocCommandExecutor(this);
            getCommand("havoc").setExecutor(ex);
            getCommand("havoc").setTabCompleter(ex);
        }
        scheduleNextProgressionReset();
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
        cancelProgressionResetTask();
        if (baseService != null) {
            baseService.shutdownFull();
        }
        if (salvageStore != null) {
            salvageStore.save();
        }
        if (progressionStore != null) {
            progressionStore.save();
        }
        if (logService != null) {
            logService.shutdown();
        }
        if (persistenceQueue != null) {
            persistenceQueue.shutdownAndDrain();
        }
        instance = null;
    }

    private void cancelProgressionResetTask() {
        if (progressionResetTaskId != -1) {
            Bukkit.getScheduler().cancelTask(progressionResetTaskId);
            progressionResetTaskId = -1;
        }
    }

    private void scheduleNextProgressionReset() {
        cancelProgressionResetTask();
        ZoneId zone;
        try {
            zone = ZoneId.of(havocConfig.getProgressionResetTimezone());
        } catch (Exception e) {
            getLogger().warning("Invalid timers.progression-reset-timezone \"" + havocConfig.getProgressionResetTimezone()
                    + "\"; falling back to America/New_York.");
            zone = ZoneId.of("America/New_York");
        }
        final ZoneId resetZone = zone;
        ZonedDateTime now = ZonedDateTime.now(resetZone);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(resetZone);
        long delayMs = Duration.between(now, nextMidnight).toMillis();
        long delayTicks = Math.max(1L, delayMs / 50L);
        progressionResetTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    if (progressionStore != null) {
                        progressionStore.resetAll();
                        progressionStore.save();
                    }
                    getLogger().info("Progression reset completed at midnight " + resetZone.getId() + ".");
                } finally {
                    scheduleNextProgressionReset();
                }
            }
        }, delayTicks).getTaskId();
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

    public MessageService getMessages() {
        return messages;
    }

    public BaseAdminGui getBaseAdminGui() {
        return baseAdminGui;
    }

    public HavocLogService getLogService() {
        return logService;
    }

    private void registerBaseLifecycleSubscribers() {
        final BaseLifecycleEventHandler handler = new BaseLifecycleEventHandler(this, baseService);
        eventBus.register(BaseSpawnedEvent.class, new EventSubscriber<BaseSpawnedEvent>() {
            @Override
            public void onEvent(BaseSpawnedEvent event) {
                handler.onBaseSpawned(event);
            }
        });
        eventBus.register(BaseBreachedEvent.class, new EventSubscriber<BaseBreachedEvent>() {
            @Override
            public void onEvent(BaseBreachedEvent event) {
                handler.onBaseBreached(event);
            }
        });
        eventBus.register(BaseRestoredEvent.class, new EventSubscriber<BaseRestoredEvent>() {
            @Override
            public void onEvent(BaseRestoredEvent event) {
                handler.onBaseRestored(event);
            }
        });
    }
}
