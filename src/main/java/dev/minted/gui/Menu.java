package dev.minted.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base for the plugin's inventory menus.
 *
 * <p>The menu is its own {@link InventoryHolder}, which is how {@link
 * MenuListener} tells one of our inventories apart from a chest without keeping
 * a per-player registry to leak. Layout is fixed: subclasses fill slots in
 * {@link #build()}, and {@link #refresh()} re-runs it in place so a balance
 * shown on screen updates the moment it changes.
 */
public abstract class Menu implements InventoryHolder {

    /** A slot handler that also sees which mouse button was used. */
    public interface ClickHandler {
        void click(Player player, ClickType type);
    }

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<Integer, Consumer<Player>>();
    private final Map<Integer, ClickHandler> handlers = new HashMap<Integer, ClickHandler>();

    /**
     * The player who this menu is currently open for. Set in {@link #open} and
     * kept across {@link #refresh}, so {@link #build()} can resolve per-player
     * text (language) while it draws the contents.
     */
    protected Player opener;

    protected Menu(String title, int rows) {
        // 1.8 rejects inventory titles longer than 32 characters; capping here
        // keeps a long shop or player name from crashing the menu on old servers.
        this.inventory = Bukkit.createInventory(this, rows * 9, cap(title));
    }

    private static String cap(String title) {
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    /** Places an icon and, when given, the action fired by clicking its slot. */
    protected void set(int slot, ItemStack icon, Consumer<Player> action) {
        inventory.setItem(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /**
     * Like {@link #set}, but the handler is told which button was clicked - used
     * where left and shift-right mean different things on the same slot.
     */
    protected void setClick(int slot, ItemStack icon, ClickHandler handler) {
        inventory.setItem(slot, icon);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    /** Draws the standard 1-slot border frame with the given pane. Call first. */
    protected void frame(ItemStack pane) {
        int size = inventory.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inventory.setItem(slot, pane);
            }
        }
    }

    /** Fills every still-empty slot with the given pane. Call last. */
    protected void fillEmpty(ItemStack pane) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, pane);
            }
        }
    }

    protected abstract void build();

    public final void open(Player player) {
        this.opener = player;
        render();
        player.openInventory(inventory);
    }

    public final void refresh() {
        render();
    }

    void click(int slot, Player player, ClickType type) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.click(player, type);
            return;
        }
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    private void render() {
        actions.clear();
        handlers.clear();
        inventory.clear();
        build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
