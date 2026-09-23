package dev.minted.auction.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;
import dev.minted.shop.ShopContext;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Menu for picking an item from the player's inventory to auction.
 */
public final class ItemPickerMenu extends Menu {

    private final ShopContext ctx;
    private final dev.minted.auction.AuctionService auctions;
    private final Player viewer;
    private final AuctionCreateMenu parent;
    private final Map<Integer, ItemStack> displayItems = new HashMap<>();
    private final int page;

    private static final int PAGE_SIZE = 28;
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public ItemPickerMenu(ShopContext ctx, dev.minted.auction.AuctionService auctions, Player viewer, AuctionCreateMenu parent) {
        this(ctx, auctions, viewer, parent, 0);
    }

    private ItemPickerMenu(ShopContext ctx, dev.minted.auction.AuctionService auctions, Player viewer, AuctionCreateMenu parent, int page) {
        super(Design.title(Design.Accent.COMMUNITY, "Select Item"), 6);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
        this.parent = parent;
        this.page = page;
        buildDisplayItems();
    }

    private void buildDisplayItems() {
        displayItems.clear();
        ItemStack[] contents = viewer.getInventory().getContents();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !dev.minted.auction.AuctionService.isBanknote(item)) {
                items.add(item);
            }
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        int slotIndex = 0;
        for (int i = start; i < end && slotIndex < DISPLAY_SLOTS.length; i++) {
            displayItems.put(DISPLAY_SLOTS[slotIndex], items.get(i));
            slotIndex++;
        }
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            final int slot = entry.getKey();
            final ItemStack item = entry.getValue();
            set(slot, buildItemIcon(item), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    selectItem(player, item);
                }
            });
        }

        // Navigation
        ItemStack[] contents = viewer.getInventory().getContents();
        int totalItems = 0;
        for (ItemStack item : contents) {
            if (item != null && !dev.minted.auction.AuctionService.isBanknote(item)) {
                totalItems++;
            }
        }
        int totalPages = (totalItems + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages > 1) {
            if (page > 0) {
                set(45, d.prev(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new ItemPickerMenu(ctx, auctions, viewer, parent, page - 1).open(player);
                    }
                });
            }
            set(49, d.pageInfo(page + 1, totalPages), null);
            if (page < totalPages - 1) {
                set(53, d.next(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new ItemPickerMenu(ctx, auctions, viewer, parent, page + 1).open(player);
                    }
                });
            }
        }

        // Back
        set(47, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AuctionCreateMenu(ctx, auctions, viewer).open(player);
            }
        });

        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });

        fillEmpty(d.filler());
    }

    private ItemStack buildItemIcon(ItemStack item) {
        List<String> lore = new ArrayList<>();
        lore.add(Design.LABEL + "Amount: " + Design.HINT + item.getAmount());
        lore.add("");
        lore.add(Design.HINT + "Click to select");
        lore.add(Design.HINT + "Shift-click for amount");
        return Icon.of(item.clone(), Design.title(Design.Accent.COMMUNITY, itemName(item)), lore);
    }

    private void selectItem(Player player, ItemStack item) {
        // For now just select the full stack
        // Could add shift-click handling for amount selection
        parent.setSelectedItem(item, item.getAmount());
        new AuctionCreateMenu(ctx, auctions, player).open(player);
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}