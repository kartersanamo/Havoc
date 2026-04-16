package com.kartersanamo.havoc.world;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;

/**
 * Places clipboard data by traversing stored blocks and writing world coordinates
 * {@code origin + (x,y,z)} — no WorldEdit paste-vector / offset transform.
 */
public final class SchematicBlockPlacer {

    private SchematicBlockPlacer() {
    }

    public static EditSession createSession(World world) throws IOException {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(p instanceof WorldEditPlugin)) {
            throw new IOException("WorldEdit must be installed to paste bases.");
        }
        WorldEditPlugin we = (WorldEditPlugin) p;
        EditSession session = we.getWorldEdit().getEditSessionFactory().getEditSession(BukkitUtil.getLocalWorld(world), -1);
        session.setFastMode(true);
        return session;
    }

    /**
     * Paste a column range where column index is x + z*width.
     */
    public static void pasteColumns(EditSession session, CuboidClipboard clip, int originX, int originY, int originZ,
            int startColumn, int columnCount) throws MaxChangedBlocksException {
        int w = clip.getWidth();
        int h = clip.getHeight();
        int len = clip.getLength();
        int total = w * len;
        int from = Math.max(0, startColumn);
        int to = Math.min(total, from + Math.max(0, columnCount));
        for (int col = from; col < to; col++) {
            int x = col % w;
            int z = col / w;
            for (int y = 0; y < h; y++) {
                BaseBlock bb;
                try {
                    bb = clip.getBlock(new Vector(x, y, z));
                } catch (Exception e) {
                    continue;
                }
                if (bb == null || bb.isAir()) {
                    continue;
                }
                session.setBlock(new Vector(originX + x, originY + y, originZ + z), bb);
            }
        }
    }

    public static void pasteAt(World world, CuboidClipboard clip, int originX, int originY, int originZ)
            throws IOException, MaxChangedBlocksException {
        EditSession session = createSession(world);
        pasteColumns(session, clip, originX, originY, originZ, 0, clip.getWidth() * clip.getLength());
        session.flushQueue();
    }
}
