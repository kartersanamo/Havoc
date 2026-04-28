package com.kartersanamo.havoc.audit;

public final class HavocLogEntry {
    public final long epochMs;
    public final String type;
    public final String user;
    public final String baseId;
    public final String world;
    public final int x;
    public final int y;
    public final int z;
    public final String message;

    public HavocLogEntry(long epochMs, String type, String user, String baseId, String world, int x, int y, int z, String message) {
        this.epochMs = epochMs;
        this.type = type;
        this.user = user;
        this.baseId = baseId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.message = message;
    }
}
