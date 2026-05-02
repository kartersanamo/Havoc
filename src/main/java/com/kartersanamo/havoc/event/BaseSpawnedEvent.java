package com.kartersanamo.havoc.event;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.Location;

public final class BaseSpawnedEvent {
    public final ActiveHavocBase base;
    public final Location location;

    public BaseSpawnedEvent(ActiveHavocBase base, Location location) {
        this.base = base;
        this.location = location;
    }
}
