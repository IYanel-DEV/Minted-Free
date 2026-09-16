package dev.minted.shop.menu;

import dev.minted.shop.ShopItem;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.Locale;

/**
 * The display sort a browse view is showing, cycled by the sort button. Pure
 * presentation - it orders a filtered copy of the items and never touches stored
 * rows. {@link #NONE} keeps the shop's own placement order.
 */
enum Sort {

    NONE("Default"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    NAME("Name: A to Z");

    private final String label;

    Sort(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    Sort nextMode() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** A comparator for this mode, or null when the list should keep its order. */
    Comparator<ShopItem> comparator() {
        switch (this) {
            case PRICE_LOW:
                return new Comparator<ShopItem>() {
                    @Override
                    public int compare(ShopItem a, ShopItem b) {
                        return Double.compare(a.getBuyPrice(), b.getBuyPrice());
                    }
                };
            case PRICE_HIGH:
                return new Comparator<ShopItem>() {
                    @Override
                    public int compare(ShopItem a, ShopItem b) {
                        return Double.compare(b.getBuyPrice(), a.getBuyPrice());
                    }
                };
            case NAME:
                return new Comparator<ShopItem>() {
                    @Override
                    public int compare(ShopItem a, ShopItem b) {
                        return label(a).compareTo(label(b));
                    }
                };
            default:
                return null;
        }
    }

    static String label(ShopItem item) {
        ItemStack stack = item.copy();
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(stack.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
