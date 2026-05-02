package com.kartersanamo.havoc.event;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.Location;

public final class BaseRestoredEvent {
    public final ActiveHavocBase base;
    public final Location location;
    public final int claimsCount;

    public BaseRestoredEvent(ActiveHavocBase base, Location location, int claimsCount) {
        this.base = base;
        this.location = location;
        this.claimsCount = claimsCount;
    }
}
