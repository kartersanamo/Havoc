package com.kartersanamo.havoc.raid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Dispenser;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class DispenserVirtualTnt {

    private DispenserVirtualTnt() {
    }

    public static boolean isTntOnlyOrEmpty(org.bukkit.block.Dispenser blockState) {
        Inventory inventory = blockState.getInventory();
        ItemStack[] contents = inventory.getContents();
        if (contents == null) {
            return true;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (stack.getType() != Material.TNT) {
                return false;
            }
        }
        return true;
    }

    public static Location dispenseLocation(Block dispenserBlock) {
        Dispenser data = (Dispenser) dispenserBlock.getState().getData();
        BlockFace face = data.getFacing();
        return dispenserBlock.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
    }

    public static TNTPrimed spawnPrimedTnt(Block dispenserBlock) {
        World world = dispenserBlock.getWorld();
        if (world == null) {
            return null;
        }
        Dispenser data = (Dispenser) dispenserBlock.getState().getData();
        BlockFace face = data.getFacing();
        Location birth = dispenseLocation(dispenserBlock);
        TNTPrimed tnt = world.spawn(birth, TNTPrimed.class);
        if (tnt == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = 0.1D;
        double x = face.getModX() * 0.3D + (random.nextDouble() - 0.5D) * spread;
        double y = face.getModY() * 0.3D + (random.nextDouble() - 0.5D) * spread;
        double z = face.getModZ() * 0.3D + (random.nextDouble() - 0.5D) * spread;
        if (face != BlockFace.DOWN && face != BlockFace.UP) {
            y += 0.1D;
        }
        tnt.setVelocity(new Vector(x, y, z));
        return tnt;
    }
}
