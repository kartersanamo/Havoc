package com.kartersanamo.havoc.event;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BaseBreachedEvent {
    public final ActiveHavocBase base;
    public final Location breachLocation;
    public final Player progressionCredit;
    public final Object havocFaction;

    public BaseBreachedEvent(ActiveHavocBase base, Location breachLocation, Player progressionCredit, Object havocFaction) {
        this.base = base;
        this.breachLocation = breachLocation;
        this.progressionCredit = progressionCredit;
        this.havocFaction = havocFaction;
    }
}
