package dev.minted.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Builds menu icons. Materials are chosen so an icon renders the same on every
 * supported server (or come pre-resolved through the compat layer); this class
 * only names and lores a stack.
 */
public final class Icon {

    private Icon() {
    }

    public static ItemStack of(Material material, String name, String... lore) {
        return of(new ItemStack(material), name, Arrays.asList(lore));
    }

    /** Names and lores an already-built stack (e.g. a compat-resolved pane). */
    public static ItemStack of(ItemStack base, String name, String... lore) {
        return of(base, name, Arrays.asList(lore));
    }

    public static ItemStack of(Material material, String name, List<String> lore) {
        return of(new ItemStack(material), name, lore);
    }

    public static ItemStack of(ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
