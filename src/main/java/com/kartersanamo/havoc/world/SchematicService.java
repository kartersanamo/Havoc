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

    public CuboidClipboard loadClipboard(File schematicFile) throws IOException, DataException {
        SchematicFormat format = SchematicFormat.getFormat(schematicFile);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + schematicFile.getName());
        }
        return format.load(schematicFile);
    }

    /**
     * @param addWorldEditOffset if true, paste vector = origin + WE offset + extra; if false, origin - WE offset + extra.
     * @param extraX extra blocks added after WE offset (fixes small systematic XZ shifts from different //copy pivots).
     */
    public void paste(World world, CuboidClipboard clipboard, int originMinX, int originMinY, int originMinZ,
            boolean addWorldEditOffset, int extraX, int extraY, int extraZ) throws IOException, DataException, MaxChangedBlocksException {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(p instanceof WorldEditPlugin)) {
            throw new IOException("WorldEdit must be installed to paste bases.");
        }
        WorldEditPlugin we = (WorldEditPlugin) p;
        EditSession session = we.getWorldEdit().getEditSessionFactory().getEditSession(BukkitUtil.getLocalWorld(world), -1);
        Vector corner = new Vector(originMinX, originMinY, originMinZ);
        if (addWorldEditOffset) {
            corner = corner.add(clipboard.getOffset());
        } else {
            corner = corner.subtract(clipboard.getOffset());
        }
        corner = corner.add(new Vector(extraX, extraY, extraZ));
        clipboard.paste(session, corner, false);
    }

    public void paste(World world, File schematicFile, int originMinX, int originMinY, int originMinZ,
            boolean addWorldEditOffset, int extraX, int extraY, int extraZ) throws IOException, DataException, MaxChangedBlocksException {
        paste(world, loadClipboard(schematicFile), originMinX, originMinY, originMinZ, addWorldEditOffset, extraX, extraY, extraZ);
    }
}
