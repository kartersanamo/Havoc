package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BaseAdminListHolder implements InventoryHolder {
    final List<UUID> baseIds = new ArrayList<UUID>();
    final int page;
    final BaseAdminGuiSortMode sortMode;

    BaseAdminListHolder(List<ActiveHavocBase> bases, int page, BaseAdminGuiSortMode sortMode) {
        this.page = page;
        this.sortMode = sortMode;
        for (ActiveHavocBase b : bases) {
            baseIds.add(b.id);
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
