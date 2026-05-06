package com.kartersanamo.havoc.listener;

import com.kartersanamo.havoc.Havoc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class InventoryClickRouterListener implements Listener {

    private final Havoc plugin;

    public InventoryClickRouterListener(Havoc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) {
            return;
        }
        if (event.getView().getTopInventory() != null
                && (plugin.getBaseTemplateEditorGui().isTemplateDifficultyList(event.getView().getTopInventory())
                || plugin.getBaseTemplateEditorGui().isTemplateEditor(event.getView().getTopInventory()))) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getBaseTemplateEditorGui().handleClick((org.bukkit.entity.Player) event.getWhoClicked(),
                        event.getView().getTopInventory(), event.getRawSlot(), event.getClick());
            }
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getBaseAdminGui().isAdminInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getBaseAdminGui().handleClick((org.bukkit.entity.Player) event.getWhoClicked(),
                        event.getView().getTopInventory(), event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getSalvageShop().isShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getSalvageShop().handleClick((org.bukkit.entity.Player) event.getWhoClicked(), event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory() != null && plugin.getLeaderboardGui().isLeaderboardInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                plugin.getLeaderboardGui().handleClick((org.bukkit.entity.Player) event.getWhoClicked(),
                        event.getView().getTopInventory(), event.getRawSlot());
            }
        }
    }
}
