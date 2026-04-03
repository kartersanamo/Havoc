package com.kartersanamo.havoc.faction;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Talks to SaberFactions (or compatible com.massivecraft.factions API) via reflection so Havoc
 * compiles without the SaberFactions Maven artifact (its build pulls optional deps that break CI).
 */
public final class FactionsBridge {

    private final JavaPlugin plugin;
    private boolean ok;
    private Object boardInstance;
    private Method boardGetFactionAt;
    private Method boardSetFactionAt;
    private Method boardRemoveAt;
    private Method fLocationWrapChunk;
    private Object factionsInstance;
    private Method factionsGetByTag;
    private Method factionsGetWilderness;
    private Method factionIsWilderness;
    private Method factionGetId;
    private Object fPlayersInstance;
    private Method fPlayersGetByPlayer;
    private Method fPlayerGetFaction;

    public FactionsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        ok = false;
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Method getBoard = boardClass.getMethod("getInstance");
            boardInstance = getBoard.invoke(null);
            boardGetFactionAt = boardClass.getMethod("getFactionAt", Class.forName("com.massivecraft.factions.FLocation"));
            boardSetFactionAt = boardClass.getMethod("setFactionAt", Class.forName("com.massivecraft.factions.Faction"), Class.forName("com.massivecraft.factions.FLocation"));
            boardRemoveAt = boardClass.getMethod("removeAt", Class.forName("com.massivecraft.factions.FLocation"));

            Class<?> fLocationClass = Class.forName("com.massivecraft.factions.FLocation");
            fLocationWrapChunk = fLocationClass.getMethod("wrap", Chunk.class);

            Class<?> factionsClass = Class.forName("com.massivecraft.factions.Factions");
            factionsInstance = factionsClass.getMethod("getInstance").invoke(null);
            factionsGetByTag = factionsClass.getMethod("getByTag", String.class);
            factionsGetWilderness = factionsClass.getMethod("getWilderness");

            Class<?> factionClass = Class.forName("com.massivecraft.factions.Faction");
            factionIsWilderness = factionClass.getMethod("isWilderness");
            // Do not reflect Object.equals — Faction is often an interface; use stable string ids instead.
            factionGetId = factionClass.getMethod("getId");

            Class<?> fPlayersClass = Class.forName("com.massivecraft.factions.FPlayers");
            fPlayersInstance = fPlayersClass.getMethod("getInstance").invoke(null);
            fPlayersGetByPlayer = fPlayersClass.getMethod("getByPlayer", Player.class);
            Class<?> fPlayerClass = Class.forName("com.massivecraft.factions.FPlayer");
            fPlayerGetFaction = fPlayerClass.getMethod("getFaction");

            ok = true;
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Could not hook Factions API: " + t.getMessage());
            return false;
        }
    }

    public boolean isReady() {
        return ok;
    }

    public Object getFactionAtChunk(Chunk chunk) throws Exception {
        Object floc = fLocationWrapChunk.invoke(null, chunk);
        return boardGetFactionAt.invoke(boardInstance, floc);
    }

    public Object getFactionAtLocation(Location loc) throws Exception {
        if (loc.getWorld() == null) {
            return null;
        }
        return getFactionAtChunk(loc.getChunk());
    }

    public Object getHavocFaction(String tag) throws Exception {
        return factionsGetByTag.invoke(factionsInstance, tag);
    }

    public Object getWilderness() throws Exception {
        return factionsGetWilderness.invoke(factionsInstance);
    }

    public void claimChunkForFaction(Chunk chunk, Object faction) throws Exception {
        Object floc = fLocationWrapChunk.invoke(null, chunk);
        boardSetFactionAt.invoke(boardInstance, faction, floc);
    }

    public void unclaimChunk(Chunk chunk) throws Exception {
        Object floc = fLocationWrapChunk.invoke(null, chunk);
        boardRemoveAt.invoke(boardInstance, floc);
    }

    public boolean isWilderness(Object faction) throws Exception {
        if (faction == null) {
            return true;
        }
        return (Boolean) factionIsWilderness.invoke(faction);
    }

    public boolean factionsEqual(Object a, Object b) throws Exception {
        if (a == null || b == null) {
            return false;
        }
        String idA = (String) factionGetId.invoke(a);
        String idB = (String) factionGetId.invoke(b);
        return idA != null && idA.equals(idB);
    }

    public Object getPlayerFaction(Player player) throws Exception {
        Object fp = fPlayersGetByPlayer.invoke(fPlayersInstance, player);
        if (fp == null) {
            return null;
        }
        return fPlayerGetFaction.invoke(fp);
    }
}
