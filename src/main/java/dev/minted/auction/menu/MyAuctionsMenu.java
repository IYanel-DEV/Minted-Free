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
 * Menu for viewing and managing your own active auctions.
 */
public final class MyAuctionsMenu extends Menu {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Player viewer;
    private final int page;
    private final List<AuctionItem> myAuctions;

    private static final int PAGE_SIZE = 28;
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public MyAuctionsMenu(ShopContext ctx, AuctionService auctions, Player viewer, int page) {
        super(Design.title(Design.Accent.COMMUNITY, "My Auctions"), 6);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
        this.page = page;
        this.myAuctions = auctions.getPlayerAuctions(viewer.getUniqueId());
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, myAuctions.size());
        int slotIndex = 0;

        for (int i = start; i < end && slotIndex < DISPLAY_SLOTS.length; i++) {
            AuctionItem auction = myAuctions.get(i);
            set(DISPLAY_SLOTS[slotIndex], buildAuctionIcon(auction), openAuction(auction));
            slotIndex++;
        }

        // Navigation
        int totalPages = (myAuctions.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages > 1) {
            if (page > 0) {
                set(45, d.prev(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyAuctionsMenu(ctx, auctions, viewer, page - 1).open(player);
                    }
                });
            }
            set(49, d.pageInfo(page + 1, totalPages), null);
            if (page < totalPages - 1) {
                set(53, d.next(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyAuctionsMenu(ctx, auctions, viewer, page + 1).open(player);
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

        // Create new
        set(48, Icon.of(Material.CHEST, Design.title(Design.Accent.COMMUNITY, "Create Auction"),
                Design.lore("List a new item for auction.", Collections.emptyList(), "Click to start.")),
                new Consumer<Player>() {
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

    private ItemStack buildAuctionIcon(AuctionItem auction) {
        ItemStack icon = auction.getItem().clone();
        List<String> lore = new ArrayList<>();
        long timeLeft = auction.getTimeRemaining();
        long hours = TimeUnit.MILLISECONDS.toHours(timeLeft);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;

        lore.add(Design.LABEL + "Time: " + Design.HINT + hours + "h " + minutes + "m");
        lore.add("");
        lore.add(Design.LABEL + "Start: " + Design.MONEY + ctx.format().format(auction.getStartPrice()));
        lore.add(Design.LABEL + "Current: " + Design.MONEY + ctx.format().format(auction.getEffectivePrice()));
        if (auction.hasBuyout()) {
            lore.add(Design.LABEL + "Buyout: " + Design.MONEY + ctx.format().format(auction.getBuyoutPrice()));
        }
        lore.add("");
        if (auction.getHighestBidder() != null) {
            String bidderName = org.bukkit.Bukkit.getOfflinePlayer(auction.getHighestBidder()).getName();
            if (bidderName == null) bidderName = "Unknown";
            if (auction.getHighestBidder().equals(viewer.getUniqueId())) {
                lore.add(Design.IN + "You are bidding on this!");
            } else {
                lore.add(Design.LABEL + "Bidder: " + Design.HINT + bidderName);
            }
        } else {
            lore.add(Design.OUT + "No bids yet");
        }
        lore.add("");
        if (auction.getHighestBidder() == null) {
            lore.add(Design.HINT + "Click to cancel");
        } else {
            lore.add(Design.HINT + "Click for details");
        }

        return Icon.of(icon, Design.title(Design.Accent.COMMUNITY, itemName(auction.getRawItem())), lore);
    }

    private Consumer<Player> openAuction(final AuctionItem auction) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                if (auction.getHighestBidder() == null) {
                    // Offer to cancel
                    player.closeInventory();
                    ctx.messages().send(player, "auction.cancel.confirm");
                    ctx.prompt().await(player, new Consumer<String>() {
                        @Override
                        public void accept(String input) {
                            if (input != null && (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y") || input.equalsIgnoreCase("confirm"))) {
                                auctions.cancelAuction(player, auction.getId());
                            }
                            new MyAuctionsMenu(ctx, auctions, viewer, page).open(player);
                        }
                    });
                } else {
                    new AuctionDetailMenu(ctx, auctions, viewer, auction).open(player);
                }
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