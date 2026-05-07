package com.kartersanamo.havoc.base.lifecycle;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.storage.ProgressionStore;
import com.kartersanamo.havoc.storage.SalvageStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RewardService {

    public interface NextBasePicker {
        ActiveHavocBase pick(BaseDifficulty difficulty, UUID exclude);
    }

    public void processBreachRewards(Havoc plugin, Object havocFaction, ActiveHavocBase base, Location breachLoc,
            Player progressionCredit, NextBasePicker picker) {
        World world = breachLoc.getWorld();
        if (world == null) {
            return;
        }
        int salvageAmt = plugin.getHavocConfig().randomSalvage(base.difficulty);
        SalvageStore salvage = plugin.getSalvageStore();
        ProgressionStore prog = plugin.getProgressionStore();
        List<Player> rewarded = new ArrayList<Player>();
        double rsq = (double) plugin.getHavocConfig().getRewardRadius() * plugin.getHavocConfig().getRewardRadius();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(breachLoc) > rsq) {
                continue;
            }
            try {
                Object pf = plugin.getFactionsBridge().getPlayerFaction(p);
                if (pf != null && plugin.getFactionsBridge().factionsEqual(pf, havocFaction)) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            rewarded.add(p);
        }

        BaseDifficulty nextTier = base.difficulty;
        if (progressionCredit != null) {
            plugin.getPlayerStatsStore().addBreachTrigger(progressionCredit.getUniqueId());
            nextTier = prog.nextHintDifficulty(progressionCredit.getUniqueId(), base.difficulty);
        }
        final ActiveHavocBase target = picker.pick(nextTier, base.id);
        for (Player p : rewarded) {
            salvage.add(p.getUniqueId(), salvageAmt);
            plugin.getPlayerStatsStore().addBreachParticipation(p.getUniqueId(), salvageAmt);
            java.util.Map<String, String> rewardVars = new java.util.HashMap<String, String>();
            rewardVars.put("amount", String.valueOf(salvageAmt));
            rewardVars.put("balance", String.valueOf(salvage.get(p.getUniqueId())));
            plugin.getMessages().send(p, "raid.reward.salvage", rewardVars);
            if (target != null) {
                Location l = new Location(Bukkit.getWorld(target.worldName), target.obsidianCenterX, target.obsidianCenterY + 2, target.obsidianCenterZ);
                java.util.Map<String, String> leadVars = new java.util.HashMap<String, String>();
                leadVars.put("difficulty", String.valueOf(nextTier));
                leadVars.put("x", String.valueOf(l.getBlockX()));
                leadVars.put("y", String.valueOf(l.getBlockY()));
                leadVars.put("z", String.valueOf(l.getBlockZ()));
                plugin.getMessages().send(p, "raid.reward.next-lead", leadVars);
            } else {
                java.util.Map<String, String> noneVars = new java.util.HashMap<String, String>();
                noneVars.put("difficulty", String.valueOf(nextTier));
                plugin.getMessages().send(p, "raid.reward.no-next-lead", noneVars);
            }
        }
        salvage.saveAsync();
        prog.saveAsync();
        plugin.getPlayerStatsStore().saveAsync();
    }
}
