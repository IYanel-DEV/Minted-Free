package dev.minted.auction.menu;

import dev.minted.auction.AuctionItem;
import dev.minted.auction.AuctionService;
import dev.minted.auction.AuctionState;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Menu for viewing auctions you've bid on.
 */
public final class MyBidsMenu extends Menu {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Player viewer;
    private final int page;
    private final List<AuctionItem> myBids;

    private static final int PAGE_SIZE = 28;
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public MyBidsMenu(ShopContext ctx, AuctionService auctions, Player viewer, int page) {
        super(Design.title(Design.Accent.COMMUNITY, "My Bids"), 6);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
        this.page = page;
        this.myBids = auctions.getPlayerBids(viewer.getUniqueId());
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, myBids.size());
        int slotIndex = 0;

        for (int i = start; i < end && slotIndex < DISPLAY_SLOTS.length; i++) {
            AuctionItem auction = myBids.get(i);
            set(DISPLAY_SLOTS[slotIndex], buildAuctionIcon(auction), openAuction(auction));
            slotIndex++;
        }

        // Navigation
        int totalPages = (myBids.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages > 1) {
            if (page > 0) {
                set(45, d.prev(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyBidsMenu(ctx, auctions, viewer, page - 1).open(player);
                    }
                });
            }
            set(49, d.pageInfo(page + 1, totalPages), null);
            if (page < totalPages - 1) {
                set(53, d.next(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyBidsMenu(ctx, auctions, viewer, page + 1).open(player);
                    }
                });
            }
        }

        // Back to browse
        set(47, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AuctionBrowseMenu(ctx, auctions, viewer, 0, "").open(player);
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

    private ItemStack buildAuctionIcon(AuctionItem auction) {
        ItemStack icon = auction.getItem().clone();
        List<String> lore = new ArrayList<>();
        long timeLeft = auction.getTimeRemaining();
        long hours = TimeUnit.MILLISECONDS.toHours(timeLeft);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;

        lore.add(Design.LABEL + "Time: " + Design.HINT + hours + "h " + minutes + "m");
        lore.add("");
        lore.add(Design.LABEL + "Your bid: " + Design.MONEY + ctx.format().format(auction.getCurrentBid()));
        if (auction.hasBuyout()) {
            lore.add(Design.LABEL + "Buyout: " + Design.MONEY + ctx.format().format(auction.getBuyoutPrice()));
        }
        lore.add("");
        if (auction.getHighestBidder() != null && auction.getHighestBidder().equals(viewer.getUniqueId())) {
            lore.add(Design.IN + "You are winning!");
        } else {
            lore.add(Design.OUT + "You have been outbid!");
            lore.add(Design.LABEL + "Current: " + Design.MONEY + ctx.format().format(auction.getEffectivePrice()));
        }
        lore.add("");
        lore.add(Design.HINT + "Click for details / increase bid");

        return Icon.of(icon, Design.title(Design.Accent.COMMUNITY, itemName(auction.getRawItem())), lore);
    }

    private Consumer<Player> openAuction(final AuctionItem auction) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AuctionDetailMenu(ctx, auctions, viewer, auction).open(player);
            }
        };
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}