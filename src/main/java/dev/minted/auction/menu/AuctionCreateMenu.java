package dev.minted.auction.menu;

import dev.minted.auction.AuctionItem;
import dev.minted.auction.AuctionService;
import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;
import dev.minted.shop.ShopContext;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menu for creating a new auction. Step 1: pick item from inventory.
 * Step 2: enter start price. Step 3: enter buyout (optional). Step 4: enter duration.
 */
public final class AuctionCreateMenu extends Menu {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Player viewer;

    // State
    private ItemStack selectedItem;
    private int selectedAmount = 1;
    private double startPrice = 0;
    private double buyoutPrice = 0;
    private int durationHours = 24;

    public AuctionCreateMenu(ShopContext ctx, AuctionService auctions, Player viewer) {
        super(Design.title(Design.Accent.COMMUNITY, "Create Auction"), 5);
        this.ctx = ctx;
        this.auctions = auctions;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        // Step 1: Item
        if (selectedItem == null) {
            set(11, Icon.of(Material.CHEST, Design.title(Design.Accent.COMMUNITY, "Select Item"),
                    Design.lore("Click to pick an item from your inventory.", java.util.Collections.emptyList(), "Shift-click to select amount.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            openItemPicker(player);
                        }
                    });
        } else {
            set(11, buildItemIcon(selectedItem), null);
            set(20, Icon.of(Material.ARROW, Design.title(Design.Accent.COMMUNITY, "Change Item"),
                    Design.lore("Pick a different item.", java.util.Collections.emptyList(), "")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            selectedItem = null;
                            new AuctionCreateMenu(ctx, auctions, viewer).open(player);
                        }
                    });
        }

        // Step 2: Start price
        if (selectedItem != null && startPrice <= 0) {
            set(13, Icon.of(Material.GOLD_NUGGET, Design.title(Design.Accent.COMMUNITY, "Starting Bid"),
                    Design.lore("Enter the minimum bid amount.", java.util.Collections.emptyList(), "Click to type amount.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            askStartPrice(player);
                        }
                    });
        } else if (startPrice > 0) {
            set(13, Icon.of(Material.GOLD_INGOT, Design.title(Design.Accent.COMMUNITY, "Starting Bid"),
                    Design.lore("Set to: " + ctx.format().format(startPrice), java.util.Collections.emptyList(), "Click to change.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            askStartPrice(player);
                        }
                    });
        }

        // Step 3: Buyout price (optional)
        if (selectedItem != null && startPrice > 0) {
            if (buyoutPrice <= 0) {
                set(15, Icon.of(Material.DIAMOND, Design.title(Design.Accent.COMMUNITY, "Buyout Price (Optional)"),
                        Design.lore("Set a buy-now price, or leave at 0 for bids only.", java.util.Collections.emptyList(), "Click to enter amount.")),
                        new Consumer<Player>() {
                            @Override
                            public void accept(Player player) {
                                askBuyoutPrice(player);
                            }
                        });
            } else {
                set(15, Icon.of(Material.DIAMOND, Design.title(Design.Accent.COMMUNITY, "Buyout Price"),
                        Design.lore("Set to: " + ctx.format().format(buyoutPrice), java.util.Collections.emptyList(), "Click to change/remove.")),
                        new Consumer<Player>() {
                            @Override
                            public void accept(Player player) {
                                askBuyoutPrice(player);
                            }
                        });
            }
        }

        // Step 4: Duration
        if (selectedItem != null && startPrice > 0) {
            set(31, Icon.of(Material.WATCH, Design.title(Design.Accent.COMMUNITY, "Duration"),
                    Design.lore("Current: " + durationHours + " hours", java.util.Collections.singletonList("Min: 10 min, Max: 168 hours (1 week)"), "Click to change.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            askDuration(player);
                        }
                    });
        }

        // Create button
        if (selectedItem != null && startPrice > 0) {
            boolean validBuyout = buyoutPrice == 0 || buyoutPrice > startPrice;
            if (validBuyout) {
                set(40, d.confirm("Create Auction"), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        createAuction(player);
                    }
                });
            } else {
                set(40, Icon.of(Material.BARRIER, Design.title(Design.Accent.NEUTRAL, "Invalid Buyout"),
                        Design.lore("Buyout must be higher than starting bid.", java.util.Collections.emptyList(), "Click buyout to fix.")),
                        null);
            }
        }

        // Back
        set(44, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AuctionBrowseMenu(ctx, auctions, viewer, 0, "").open(player);
            }
        });

        fillEmpty(d.filler());
    }

    private void openItemPicker(Player player) {
        // Use the existing SellPickerMenu approach - show player's inventory items
        // For simplicity, we'll use a custom item picker
        new ItemPickerMenu(ctx, auctions, viewer, this).open(player);
    }

    private ItemStack buildItemIcon(ItemStack item) {
        ItemStack icon = item.clone();
        List<String> lore = new ArrayList<>();
        lore.add(Design.LABEL + "Amount: " + Design.HINT + item.getAmount());
        lore.add("");
        if (startPrice > 0) {
            lore.add(Design.LABEL + "Start: " + Design.MONEY + ctx.format().format(startPrice));
            if (buyoutPrice > 0) {
                lore.add(Design.LABEL + "Buyout: " + Design.MONEY + ctx.format().format(buyoutPrice));
            }
            lore.add(Design.LABEL + "Duration: " + Design.HINT + durationHours + "h");
        }
        return Icon.of(icon, Design.title(Design.Accent.COMMUNITY, itemName(item)), lore);
    }

    private void askStartPrice(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "auction.create.start_price");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new AuctionCreateMenu(ctx, auctions, viewer).open(player);
                    return;
                }
                try {
                    double amount = Double.parseDouble(input.trim());
                    if (amount < auctions.getMinStartPrice()) {
                        ctx.messages().send(player, "auction.create.price_too_low", "min", String.valueOf(auctions.getMinStartPrice()));
                    } else {
                        startPrice = amount;
                    }
                } catch (NumberFormatException e) {
                    ctx.messages().send(player, "auction.bid.invalid_amount");
                }
                new AuctionCreateMenu(ctx, auctions, viewer).open(player);
            }
        });
    }

    private void askBuyoutPrice(final Player player) {
        player.closeInventory();
        if (buyoutPrice > 0) {
            ctx.messages().send(player, "auction.create.buyout_change");
        } else {
            ctx.messages().send(player, "auction.create.buyout");
        }
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new AuctionCreateMenu(ctx, auctions, viewer).open(player);
                    return;
                }
                if (input.equalsIgnoreCase("0") || input.equalsIgnoreCase("none") || input.equalsIgnoreCase("remove")) {
                    buyoutPrice = 0;
                } else {
                    try {
                        double amount = Double.parseDouble(input.trim());
                        if (amount <= startPrice) {
                            ctx.messages().send(player, "auction.create.buyout_too_low");
                        } else {
                            buyoutPrice = amount;
                        }
                    } catch (NumberFormatException e) {
                        ctx.messages().send(player, "auction.bid.invalid_amount");
                    }
                }
                new AuctionCreateMenu(ctx, auctions, viewer).open(player);
            }
        });
    }

    private void askDuration(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "auction.create.duration", "min", "10min", "max", "168h");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new AuctionCreateMenu(ctx, auctions, viewer).open(player);
                    return;
                }
                try {
                    String trimmed = input.trim().toLowerCase();
                    int hours;
                    if (trimmed.endsWith("h") || trimmed.endsWith("hr") || trimmed.endsWith("hrs")) {
                        hours = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                    } else if (trimmed.endsWith("m") || trimmed.endsWith("min") || trimmed.endsWith("mins")) {
                        int minutes = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                        hours = (minutes + 59) / 60;
                    } else {
                        hours = Integer.parseInt(trimmed);
                    }
                    if (hours < 1) hours = 1;
                    if (hours > auctions.getMaxDurationHours()) hours = auctions.getMaxDurationHours();
                    durationHours = hours;
                } catch (NumberFormatException e) {
                    ctx.messages().send(player, "auction.bid.invalid_amount");
                }
                new AuctionCreateMenu(ctx, auctions, viewer).open(player);
            }
        });
    }

    private void createAuction(final Player player) {
        if (!auctions.isReady()) {
            ctx.messages().send(player, "auction.not_ready");
            return;
        }
        if (auctions.getActiveCount(player.getUniqueId()) >= auctions.getMaxItemsPerPlayer()) {
            ctx.messages().send(player, "auction.create.max_reached", "max", String.valueOf(auctions.getMaxItemsPerPlayer()));
            return;
        }

        AuctionItem auction = auctions.createAuction(player, selectedItem, selectedAmount, startPrice, buyoutPrice, durationHours);
        if (auction != null) {
            ctx.messages().send(player, "auction.create.success",
                    "item", itemName(selectedItem), "price", ctx.format().format(startPrice));
        } else {
            ctx.messages().send(player, "auction.create.failed");
        }
        new AuctionBrowseMenu(ctx, auctions, player, 0, "").open(player);
    }

    public void setSelectedItem(ItemStack item, int amount) {
        this.selectedItem = item;
        this.selectedAmount = amount;
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }

    public int getSelectedAmount() {
        return selectedAmount;
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}