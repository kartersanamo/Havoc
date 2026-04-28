package com.kartersanamo.havoc.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

final class BaseAdminDetailHolder implements InventoryHolder {
    final UUID baseId;
    final int returnPage;
    final BaseAdminGuiSortMode returnSortMode;

    BaseAdminDetailHolder(UUID baseId, int returnPage, BaseAdminGuiSortMode returnSortMode) {
        this.baseId = baseId;
        this.returnPage = returnPage;
        this.returnSortMode = returnSortMode;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
