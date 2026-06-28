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
        return world.spawn(dispenseLocation(dispenserBlock), TNTPrimed.class);
    }
}
