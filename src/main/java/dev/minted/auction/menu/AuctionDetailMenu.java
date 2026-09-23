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
 * Detail view for a single auction. Shows item, time, bid/buyout options.
 */
public final class AuctionDetailMenu extends Menu {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Player viewer;
    private final AuctionItem auction;

    public AuctionDetailMenu(ShopContext ctx, AuctionService auctions, Player viewer, AuctionItem auction) {
        super(Design.title(Design.Accent.COMMUNITY, "Auction: " + itemNameStatic(auction.getRawItem())), 5);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
        this.auction = auction;
    }

    private static String itemNameStatic(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        // Item display in center
        set(12, buildItemIcon(auction), null);

        // Info panel on right
        set(14, infoIcon(), null);
        set(23, infoIcon(), null);
        set(32, infoIcon(), null);

        // Actions
        if (auction.getHighestBidder() != null && auction.getHighestBidder().equals(viewer.getUniqueId())) {
            // You're winning - show increase bid
            set(20, Icon.of(Material.ARROW, Design.title(Design.Accent.NEUTRAL, "Increase Bid"),
                    Design.lore("Place a higher bid.", Collections.emptyList(), "Click to enter amount.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            askBidAmount(player, false);
                        }
                    });
        } else if (!viewer.getUniqueId().equals(auction.getSeller())) {
            // Not seller, not winning - bid or buyout
            set(20, Icon.of(Material.GOLD_INGOT, Design.title(Design.Accent.COMMUNITY, "Place Bid"),
                    Design.lore("Current: " + ctx.format().format(auction.getEffectivePrice()), Collections.emptyList(), "Click to bid.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            askBidAmount(player, false);
                        }
                    });

            if (auction.hasBuyout()) {
                set(24, Icon.of(Material.DIAMOND, Design.title(Design.Accent.COMMUNITY, "Buyout"),
                        Design.lore("Price: " + ctx.format().format(auction.getBuyoutPrice()), Collections.emptyList(), "Click to buy now.")),
                        new Consumer<Player>() {
                            @Override
                            public void accept(Player player) {
                                confirmBuyout(player);
                            }
                        });
            }
        }

        // Seller actions
        if (viewer.getUniqueId().equals(auction.getSeller()) && auction.getState() == AuctionState.ACTIVE) {
            if (auction.getHighestBidder() == null) {
                set(30, Icon.of(Material.BARRIER, Design.title(Design.Accent.NEUTRAL, "Cancel Auction"),
                        Design.lore("Return the item to your inventory.", Collections.emptyList(), "Click to cancel.")),
                        new Consumer<Player>() {
                            @Override
                            public void accept(Player player) {
                                confirmCancel(player);
                            }
                        });
            }
        }

        // Back
        set(40, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AuctionBrowseMenu(ctx, auctions, viewer, 0, "").open(player);
            }
        });

        fillEmpty(d.filler());
    }

    private ItemStack buildItemIcon(AuctionItem auction) {
        ItemStack icon = auction.getItem().clone();
        List<String> lore = new ArrayList<>();
        long timeLeft = auction.getTimeRemaining();
        long hours = TimeUnit.MILLISECONDS.toHours(timeLeft);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;

        lore.add(Design.LABEL + "Time remaining: " + Design.HINT + hours + "h " + minutes + "m");
        lore.add("");
        lore.add(Design.LABEL + "Starting bid: " + Design.MONEY + ctx.format().format(auction.getStartPrice()));
        lore.add(Design.LABEL + "Current bid: " + Design.MONEY + ctx.format().format(auction.getEffectivePrice()));
        if (auction.hasBuyout()) {
            lore.add(Design.LABEL + "Buyout: " + Design.MONEY + ctx.format().format(auction.getBuyoutPrice()));
        }
        lore.add("");
        lore.add(Design.LABEL + "Bids placed: " + Design.HINT + auction.getBidCount());
        if (auction.getHighestBidder() != null) {
            String bidderName = org.bukkit.Bukkit.getOfflinePlayer(auction.getHighestBidder()).getName();
            if (bidderName == null) bidderName = "Unknown";
            if (auction.getHighestBidder().equals(viewer.getUniqueId())) {
                lore.add(Design.IN + "You are winning!");
            } else {
                lore.add(Design.LABEL + "Highest bidder: " + Design.HINT + bidderName);
            }
        }
        lore.add("");
        lore.add(Design.HINT + "Seller: " + org.bukkit.Bukkit.getOfflinePlayer(auction.getSeller()).getName());

        return Icon.of(icon, Design.title(Design.Accent.COMMUNITY, itemName(auction.getRawItem())), lore);
    }

    private ItemStack infoIcon() {
        Design d = ctx.design();
        return d.border(Design.Accent.NEUTRAL);
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void askBidAmount(final Player player, final boolean isBuyout) {
        player.closeInventory();
        String prompt = isBuyout ? "auction.buyout.confirm" : "auction.bid.prompt";
        ctx.messages().send(player, prompt);
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new AuctionDetailMenu(ctx, auctions, viewer, auction).open(player);
                    return;
                }
                try {
                    double amount = Double.parseDouble(input.trim());
                    if (isBuyout) {
                        if (auctions.buyout(player, auction.getId())) {
                            ctx.messages().send(player, "auction.buyout.success");
                        } else {
                            ctx.messages().send(player, "auction.buyout.failed");
                        }
                    } else {
                        if (auctions.placeBid(player, auction.getId(), amount)) {
                            ctx.messages().send(player, "auction.bid.success");
                        } else {
                            ctx.messages().send(player, "auction.bid.failed");
                        }
                    }
                } catch (NumberFormatException e) {
                    ctx.messages().send(player, "auction.bid.invalid_amount");
                }
                new AuctionDetailMenu(ctx, auctions, viewer, auction).open(player);
            }
        });
    }

    private void confirmBuyout(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "auction.buyout.confirm", "price", ctx.format().format(auction.getBuyoutPrice()));
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input != null && (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y") || input.equalsIgnoreCase("confirm"))) {
                    if (auctions.buyout(player, auction.getId())) {
                        ctx.messages().send(player, "auction.buyout.success");
                    } else {
                        ctx.messages().send(player, "auction.buyout.failed");
                    }
                }
                new AuctionBrowseMenu(ctx, auctions, viewer, 0, "").open(player);
            }
        });
    }

    private void confirmCancel(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "auction.cancel.confirm");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input != null && (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y") || input.equalsIgnoreCase("confirm"))) {
                    auctions.cancelAuction(player, auction.getId());
                }
                new MyAuctionsMenu(ctx, auctions, viewer, 0).open(player);
            }
        });
    }
}