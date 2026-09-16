package dev.minted.gui.theme;

import dev.minted.bank.MoneyFormat;
import dev.minted.compat.Glass;
import dev.minted.gui.Icon;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The one design language every menu speaks: a fixed palette, a border pattern,
 * standard furniture icons and a single lore shape.
 *
 * <p>Palette rules, applied everywhere: money is gold, money-in/earning green,
 * money-out/spending red, labels and hints dark/gray, headings bold. A menu uses
 * gold plus exactly one {@link Accent} colour - never a rainbow. Furniture icons
 * (arrows, page info, home, search, confirm, close) come from here so no menu
 * hand-picks its own, and every icon's lore follows {@link #lore}.
 */
public final class Design {

    // Palette. Two accents per menu at most: gold (money) + the menu's Accent.
    public static final ChatColor MONEY = ChatColor.GOLD;
    public static final ChatColor IN = ChatColor.GREEN;
    public static final ChatColor OUT = ChatColor.RED;
    public static final ChatColor LABEL = ChatColor.DARK_GRAY;
    public static final ChatColor HINT = ChatColor.GRAY;
    public static final ChatColor HEADING = ChatColor.WHITE;

    /** A menu's single accent: its heading tint and its border glass colour. */
    public enum Accent {
        SHOP(ChatColor.GREEN, Glass.Tone.GREEN),
        BANK(ChatColor.AQUA, Glass.Tone.LIGHT_BLUE),
        COMMUNITY(ChatColor.YELLOW, Glass.Tone.YELLOW),
        NEUTRAL(ChatColor.WHITE, Glass.Tone.GRAY);

        public final ChatColor colour;
        public final Glass.Tone border;

        Accent(ChatColor colour, Glass.Tone border) {
            this.colour = colour;
            this.border = border;
        }
    }

    private final Glass glass;

    public Design(Glass glass) {
        this.glass = glass;
    }

    public ItemStack border(Accent accent) {
        return blank(glass.pane(accent.border));
    }

    public ItemStack filler() {
        return blank(glass.pane(Glass.Tone.GRAY));
    }

    private static ItemStack blank(ItemStack pane) {
        return Icon.of(pane, " ");
    }

    // --- Furniture. Materials here are unchanged across 1.8 -> 1.26. ---

    public ItemStack back() {
        return Icon.of(Material.ARROW, HINT + "Back");
    }

    public ItemStack prev() {
        return Icon.of(Material.ARROW, HINT + "Previous page");
    }

    public ItemStack next() {
        return Icon.of(Material.ARROW, HINT + "Next page");
    }

    public ItemStack pageInfo(int page, int pages) {
        return Icon.of(Material.PAPER, HEADING + "" + ChatColor.BOLD + "Page " + page + " / " + pages);
    }

    public ItemStack home() {
        return Icon.of(Material.CHEST, HEADING + "" + ChatColor.BOLD + "Categories",
                HINT + "Click to browse by category.");
    }

    public ItemStack search(String query) {
        boolean active = query != null && !query.isEmpty();
        return Icon.of(Material.COMPASS, HEADING + "" + ChatColor.BOLD + "Search",
                active ? LABEL + "Showing: " + HINT + query : HINT + "Click to type a search term.");
    }

    public ItemStack sort(String label) {
        return Icon.of(Material.HOPPER, HEADING + "" + ChatColor.BOLD + "Sort",
                LABEL + "Order: " + HINT + label, HINT + "Click to change.");
    }

    public ItemStack close() {
        return Icon.of(Material.BARRIER, OUT + "" + ChatColor.BOLD + "Close");
    }

    public ItemStack confirm(String label) {
        return Icon.of(Material.EMERALD, IN + "" + ChatColor.BOLD + label);
    }

    public ItemStack cancel() {
        return Icon.of(Material.BARRIER, OUT + "" + ChatColor.BOLD + "Cancel");
    }

    public ItemStack money(String label, double value, MoneyFormat format) {
        return Icon.of(Material.GOLD_INGOT, MONEY + "" + ChatColor.BOLD + label,
                HINT + format.format(value));
    }

    /** Wallet balance tile, identical in every menu that shows a balance strip. */
    public ItemStack wallet(double value, MoneyFormat format) {
        return Icon.of(Material.GOLD_INGOT, MONEY + "" + ChatColor.BOLD + "Wallet",
                HINT + format.format(value));
    }

    /** Bank balance tile, the green counterpart to {@link #wallet}. */
    public ItemStack bank(double value, MoneyFormat format) {
        return Icon.of(Material.EMERALD, IN + "" + ChatColor.BOLD + "Bank",
                HINT + format.format(value));
    }

    /**
     * The lore norm: one plain description line, a blank, the money/action lines
     * (already palette-coloured by the caller), a blank, then one gray hint. Any
     * argument may be null/empty to drop that block.
     */
    public static List<String> lore(String description, List<String> moneyLines, String hint) {
        List<String> out = new ArrayList<String>();
        if (description != null && !description.isEmpty()) {
            out.add(LABEL + description);
        }
        if (moneyLines != null && !moneyLines.isEmpty()) {
            if (!out.isEmpty()) {
                out.add("");
            }
            out.addAll(moneyLines);
        }
        if (hint != null && !hint.isEmpty()) {
            if (!out.isEmpty()) {
                out.add("");
            }
            out.add(HINT + hint);
        }
        return out;
    }

    /** Centres a title to read as designed; callers still keep it short. */
    public static String title(Accent accent, String text) {
        return accent.colour + "" + ChatColor.BOLD + text;
    }
}
