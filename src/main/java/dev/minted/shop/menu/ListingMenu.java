package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One community listing: the stack in the middle, a Buy button that opens a
 * quantity picker, and - when the owner offers a buy-back - a Sell button to
 * sell matching items into it. All money moves through {@link dev.minted.shop.Market}.
 */
public final class ListingMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;
    private final ShopItem listing;

    public ListingMenu(ShopContext ctx, Shop shop, Player viewer, ShopItem listing) {
        super(Design.title(Design.Accent.COMMUNITY, "Listing"), 3);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
        this.listing = listing;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));
        set(13, preview(), null);

        set(11, Icon.of(Material.EMERALD, Design.title(Design.Accent.SHOP, "Buy"),
                Design.lore("Buy from this listing.",
                        one(Design.MONEY + "Price: " + ChatColor.WHITE + ctx.format().format(listing.getBuyPrice())
                                + " each"), "Click to choose how many.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        new QuantityMenu(ctx, ctx.messages().get("quantity.title", "action", "Buy"),
                                buy()).open(player);
                    }
                });

        if (listing.buysBack()) {
            set(15, Icon.of(Material.GOLD_INGOT, Design.title(Design.Accent.COMMUNITY, "Sell to owner"),
                    Design.lore("The owner buys these back.",
                            one(Design.IN + "Payout: " + ChatColor.WHITE
                                    + ctx.format().format(listing.getBuyBackPrice()) + " each"),
                            "Click to choose how many.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            new QuantityMenu(ctx, ctx.messages().get("quantity.title", "action", "Sell"),
                                    sell()).open(player);
                        }
                    });
        }

        set(22, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private ItemStack preview() {
        List<String> lore = new ArrayList<String>();
        lore.add(Design.HINT + "In stock: " + ChatColor.WHITE + listing.getStock());
        return Display.withLore(listing.copy(), lore);
    }

    private QuantityMenu.QuantityChoice buy() {
        return new QuantityMenu.QuantityChoice() {
            @Override
            public void chosen(Player player, int quantity) {
                ctx.market().buy(player, shop, listing, quantity);
                reopen(player);
            }
        };
    }

    private QuantityMenu.QuantityChoice sell() {
        return new QuantityMenu.QuantityChoice() {
            @Override
            public void chosen(Player player, int quantity) {
                ctx.market().sellTo(player, shop, listing, quantity);
                reopen(player);
            }
        };
    }

    private void reopen(Player player) {
        if (listing.getStock() <= 0 && !listing.buysBack()) {
            new HomeMenu(ctx, shop, viewer).open(player);
        } else {
            new ListingMenu(ctx, shop, viewer, listing).open(player);
        }
    }

    private static List<String> one(String line) {
        List<String> lines = new ArrayList<String>();
        lines.add(line);
        return lines;
    }
}
