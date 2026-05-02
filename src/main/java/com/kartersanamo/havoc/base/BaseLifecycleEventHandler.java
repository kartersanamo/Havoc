package com.kartersanamo.havoc.base;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.lifecycle.RewardService;
import com.kartersanamo.havoc.event.BaseBreachedEvent;
import com.kartersanamo.havoc.event.BaseRestoredEvent;
import com.kartersanamo.havoc.event.BaseSpawnedEvent;

public final class BaseLifecycleEventHandler {
    private final Havoc plugin;
    private final BaseService baseService;
    private final RewardService rewardService = new RewardService();

    public BaseLifecycleEventHandler(Havoc plugin, BaseService baseService) {
        this.plugin = plugin;
        this.baseService = baseService;
    }

    public void onBaseSpawned(BaseSpawnedEvent event) {
        if (event == null || event.base == null || event.location == null) {
            return;
        }
        plugin.getLogService().log("BASE_SPAWN", "", shortId(event.base.id), event.location,
                "difficulty=" + event.base.difficulty + ", claims=" + event.base.claimedChunks.size());
    }

    public void onBaseBreached(BaseBreachedEvent event) {
        if (event == null || event.base == null || event.breachLocation == null) {
            return;
        }
        plugin.getLogService().log("BASE_BREACH",
                event.progressionCredit == null ? "" : event.progressionCredit.getName(),
                shortId(event.base.id),
                event.breachLocation,
                "difficulty=" + event.base.difficulty + ", state=" + event.base.state);
        rewardService.processBreachRewards(plugin, event.havocFaction, event.base, event.breachLocation,
                event.progressionCredit, new RewardService.NextBasePicker() {
                    @Override
                    public ActiveHavocBase pick(BaseDifficulty difficulty, java.util.UUID exclude) {
                        return baseService.pickRandomActiveForEvent(difficulty, exclude);
                    }
                });
    }

    public void onBaseRestored(BaseRestoredEvent event) {
        if (event == null || event.base == null || event.location == null) {
            return;
        }
        plugin.getLogService().log("BASE_RESTORE_DONE", "", shortId(event.base.id), event.location,
                "claims=" + event.claimsCount);
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
