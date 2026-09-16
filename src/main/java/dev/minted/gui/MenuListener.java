package dev.minted.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Cancels all interaction with our menus and routes a click on the top
 * inventory to the {@link Menu} that owns it. Every click and drag is cancelled
 * whether it lands on the menu or the player's own inventory, so nothing can be
 * pulled out of or dropped into an open menu.
 */
public final class MenuListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Menu menu = menuOf(top);
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        // Below the top inventory's size the raw slot is the player's own grid.
        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
            menu.click(event.getRawSlot(), (Player) event.getWhoClicked(), event.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    private Menu menuOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Menu ? (Menu) holder : null;
    }
}
