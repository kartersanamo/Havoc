package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.generator.BaseTemplateDefinition;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BaseTemplateEditorHolder implements InventoryHolder {

    public final BaseTemplateDefinition draft;
    public int page;
    public int selectedIndex;

    public BaseTemplateEditorHolder(BaseTemplateDefinition draft, int page, int selectedIndex) {
        this.draft = draft;
        this.page = page;
        this.selectedIndex = selectedIndex;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
