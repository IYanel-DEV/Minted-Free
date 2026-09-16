package dev.minted.shop.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Small item helpers shared by the shop menus. */
final class Display {

    private Display() {
    }

    /** A copy of {@code base} with extra lore lines appended, leaving the original untouched. */
    static ItemStack withLore(ItemStack base, List<String> extraLore) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        lore.addAll(extraLore);
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** The player's main-hand item as a single unit, or null if their hand is empty. */
    static ItemStack heldUnit(Player player) {
        ItemStack held = player.getItemInHand();
        if (held == null || held.getType() == Material.AIR) {
            return null;
        }
        ItemStack unit = held.clone();
        unit.setAmount(1);
        return unit;
    }
}
