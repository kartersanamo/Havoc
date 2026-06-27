package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BaseAdminListHolder implements InventoryHolder {
    final List<UUID> baseIds = new ArrayList<UUID>();

    BaseAdminListHolder(List<ActiveHavocBase> displayedBases) {
        for (ActiveHavocBase b : displayedBases) {
            baseIds.add(b.id);
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
