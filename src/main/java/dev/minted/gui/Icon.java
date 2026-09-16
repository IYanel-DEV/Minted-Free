package dev.minted.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Builds menu icons. Only materials whose name is unchanged from 1.8 to 1.26
 * are used, so an icon renders the same on every supported server.
 */
public final class Icon {

    private Icon() {
    }

    public static ItemStack of(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }
}
