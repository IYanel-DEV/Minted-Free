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
 * Main auction house browse menu. Lists active auctions with pagination.
 */
public final class AuctionBrowseMenu extends Menu {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Player viewer;
    private final int page;
    private final String searchQuery;
    private final List<AuctionItem> filtered;

    private static final int PAGE_SIZE = 28;
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public AuctionBrowseMenu(ShopContext ctx, AuctionService auctions, Player viewer, int page, String searchQuery) {
        super(Design.title(Design.Accent.COMMUNITY, "Auction House"), 6);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
        this.page = page;
        this.searchQuery = searchQuery;
        this.filtered = filter(auctions.getActiveAuctions(), searchQuery);
    }

    private List<AuctionItem> filter(List<AuctionItem> all, String query) {
        if (query == null || query.isEmpty()) {
            return all;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        List<AuctionItem> result = new ArrayList<>();
        for (AuctionItem a : all) {
            String name = itemName(a.getRawItem()).toLowerCase(Locale.ROOT);
            if (name.contains(lower)) {
                result.add(a);
            }
        }
        return result;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        // Balance display
        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        int slotIndex = 0;

        for (int i = start; i < end && slotIndex < DISPLAY_SLOTS.length; i++) {
            AuctionItem auction = filtered.get(i);
            set(DISPLAY_SLOTS[slotIndex], buildAuctionIcon(auction), openAuction(auction));
            slotIndex++;
        }

        // Navigation
        int totalPages = (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages > 1) {
            if (page > 0) {
                set(45, d.prev(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new AuctionBrowseMenu(ctx, auctions, viewer, page - 1, searchQuery).open(player);
                    }
                });
            }
            set(49, d.pageInfo(page + 1, totalPages), null);
            if (page < totalPages - 1) {
                set(53, d.next(), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new AuctionBrowseMenu(ctx, auctions, viewer, page + 1, searchQuery).open(player);
                    }
                });
            }
        }

        // Search
        set(46, d.search(searchQuery), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                askSearch(player);
            }
        });

        // Create auction
        set(47, Icon.of(Material.CHEST, Design.title(Design.Accent.COMMUNITY, "Create Auction"),
                Design.lore("List an item for auction.", Collections.emptyList(), "Click to start.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new AuctionCreateMenu(ctx, auctions, viewer).open(player);
                    }
                });

        // My auctions
        set(48, Icon.of(Material.EMERALD, Design.title(Design.Accent.COMMUNITY, "My Auctions"),
                Design.lore("View and manage your active listings.", Collections.emptyList(), "Click to open.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyAuctionsMenu(ctx, auctions, viewer, 0).open(player);
                    }
                });

        // My bids
        set(50, Icon.of(Material.DIAMOND, Design.title(Design.Accent.COMMUNITY, "My Bids"),
                Design.lore("View auctions you've bid on.", Collections.emptyList(), "Click to open.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new MyBidsMenu(ctx, auctions, viewer, 0).open(player);
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
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60;

        lore.add(Design.LABEL + "Time: " + Design.HINT + String.format("%dh %dm %ds", hours, minutes, seconds));
        lore.add("");
        lore.add(Design.LABEL + "Current bid: " + Design.MONEY + ctx.format().format(auction.getEffectivePrice()));
        if (auction.hasBuyout()) {
            lore.add(Design.LABEL + "Buyout: " + Design.MONEY + ctx.format().format(auction.getBuyoutPrice()));
        }
        lore.add("");
        if (auction.getHighestBidder() != null && auction.getHighestBidder().equals(viewer.getUniqueId())) {
            lore.add(Design.IN + "You are the highest bidder!");
        } else if (auction.getHighestBidder() != null) {
            lore.add(Design.OUT + "Bid by another player");
        }
        lore.add(Design.HINT + "Click to bid or buyout");

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

    private void askSearch(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "auction.search.prompt");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new AuctionBrowseMenu(ctx, auctions, viewer, 0, "").open(player);
                    return;
                }
                new AuctionBrowseMenu(ctx, auctions, viewer, 0, input.trim()).open(player);
            }
        });
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}