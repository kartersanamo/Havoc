package com.kartersanamo.havoc.command.subcommands.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.permission.PermissionNodes;
import com.kartersanamo.havoc.world.SchematicService;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.data.DataException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminBasesSubcommand implements AdminSubcommand {

    private final Havoc plugin;
    private final Map<UUID, Integer> previewTasksByPlayer = new HashMap<UUID, Integer>();

    public AdminBasesSubcommand(Havoc plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "bases";
    }

    @Override
    public String permissionNode() {
        return PermissionNodes.ADMIN_BASES;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "command.players-only");
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 2 && "preview".equalsIgnoreCase(args[0])) {
            BaseDifficulty difficulty;
            try {
                difficulty = BaseDifficulty.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid difficulty. Use EASY, MEDIUM, or HARD.");
                return true;
            }
            previewOneLayer(player, difficulty);
            return true;
        }
        plugin.getBaseTemplateEditorGui().openDifficultyList(player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.asList("preview"), args[0]);
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            return partial(Arrays.asList("EASY", "MEDIUM", "HARD"), args[1]);
        }
        return new ArrayList<String>();
    }

    private void previewOneLayer(final Player player, BaseDifficulty difficulty) {
        File schematicFile = plugin.getHavocConfig().resolveSchematicFile(difficulty);
        if (!schematicFile.isFile()) {
            player.sendMessage("§cNo schematic found for " + difficulty.name() + ".");
            return;
        }
        final CuboidClipboard clip;
        try {
            clip = new SchematicService().loadClipboard(schematicFile);
        } catch (IOException | DataException e) {
            player.sendMessage("§cCould not load schematic: " + e.getMessage());
            return;
        }
        cancelExistingPreview(player.getUniqueId());

        final World world = player.getWorld();
        final int startX = player.getLocation().getBlockX();
        final int startY = player.getLocation().getBlockY();
        final int startZ = player.getLocation().getBlockZ();
        final int layerY = Math.max(0, Math.min(clip.getHeight() - 1, 1));
        final List<Location> changed = new ArrayList<Location>();

        for (int sx = 0; sx < clip.getWidth(); sx++) {
            for (int sz = 0; sz < clip.getLength(); sz++) {
                BaseBlock bb;
                try {
                    bb = clip.getBlock(new Vector(sx, layerY, sz));
                } catch (Exception ignored) {
                    continue;
                }
                if (bb == null || bb.isAir()) {
                    continue;
                }
                Material mat = Material.getMaterial(bb.getType());
                if (mat == null) {
                    continue;
                }
                byte data = (byte) bb.getData();
                Location loc = new Location(world, startX + sx, startY, startZ + sz);
                changed.add(loc);
                player.sendBlockChange(loc, mat, data);
            }
        }

        player.sendMessage("§aPreviewed " + difficulty.name() + " one Y-layer ghost blocks for 8 seconds.");
        final UUID playerId = player.getUniqueId();
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                previewTasksByPlayer.remove(playerId);
                for (Location loc : changed) {
                    if (loc.getWorld() == null) {
                        continue;
                    }
                    org.bukkit.block.Block real = loc.getBlock();
                    player.sendBlockChange(loc, real.getType(), real.getData());
                }
                player.sendMessage("§7Preview cleared.");
            }
        }, 8L * 20L);
        previewTasksByPlayer.put(playerId, taskId);
    }

    private void cancelExistingPreview(UUID playerId) {
        Integer existing = previewTasksByPlayer.remove(playerId);
        if (existing != null) {
            Bukkit.getScheduler().cancelTask(existing.intValue());
        }
    }

    private static List<String> partial(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
