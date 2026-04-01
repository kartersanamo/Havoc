package com.kartersanamo.havoc.world;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.schematic.SchematicFormat;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public final class SchematicService {

    public void paste(World world, File schematicFile, int originX, int originY, int originZ) throws IOException, DataException, MaxChangedBlocksException {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(p instanceof WorldEditPlugin)) {
            throw new IOException("WorldEdit must be installed to paste bases.");
        }
        WorldEditPlugin we = (WorldEditPlugin) p;
        SchematicFormat format = SchematicFormat.getFormat(schematicFile);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + schematicFile.getName());
        }
        CuboidClipboard clipboard = format.load(schematicFile);
        EditSession session = we.getWorldEdit().getEditSessionFactory().getEditSession(BukkitUtil.getLocalWorld(world), -1);
        Vector origin = new Vector(originX, originY, originZ);
        clipboard.paste(session, origin, false);
    }
}
