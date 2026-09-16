package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;
import dev.minted.shop.catalog.Category;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.ClickType;

import java.util.function.Consumer;

/**
 * Sets up a community listing before it goes live: the buy price (required),
 * an optional buy-back price, and a category (defaulting to a guess from the
 * material, click to cycle). Confirm hands off to {@link dev.minted.shop.Market},
 * which removes the items and creates the listing.
 */
public final class ListingBuilderMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;
    private final ItemStack unit;
    private final int amount;
    private final double price;
    private final double buyBack;
    private final Category category;

    public ListingBuilderMenu(ShopContext ctx, Shop shop, Player viewer, ItemStack stack) {
        this(ctx, shop, viewer, single(stack), stack.getAmount(), 0, ShopItem.NOT_OFFERED,
                Category.guess(stack.getType()));
    }

    private ListingBuilderMenu(ShopContext ctx, Shop shop, Player viewer, ItemStack unit, int amount,
                               double price, double buyBack, Category category) {
        super(Design.title(Design.Accent.COMMUNITY, "New listing"), 3);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
        this.unit = unit;
        this.amount = amount;
        this.price = price;
        this.buyBack = buyBack;
        this.category = category;
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack unit = stack.clone();
        unit.setAmount(1);
        return unit;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));

        ItemStack preview = unit.clone();
        preview.setAmount(Math.min(amount, unit.getMaxStackSize()));
        set(4, Display.withLore(preview, java.util.Arrays.asList(
                Design.HINT + "Listing " + ChatColor.WHITE + amount + Design.HINT + " units")), null);

        set(10, Icon.of(Material.GOLD_INGOT, Design.MONEY + "" + ChatColor.BOLD + "Buy price",
                Design.lore("What buyers pay per unit.",
                        java.util.Collections.singletonList(Design.MONEY + valueOr(price, "not set")),
                        "Click to type a price.")), askPrice());

        set(12, Icon.of(Material.GOLD_NUGGET, Design.title(Design.Accent.COMMUNITY, "Buy-back price"),
                Design.lore("What you pay to buy units back (optional).",
                        java.util.Collections.singletonList(Design.IN
                                + (buyBack > 0 ? ctx.format().format(buyBack) : "closed")),
                        "Click to type, or 'none'.")), askBuyBack());

        Material icon = ctx.materials().get(category.iconKey());
        set(14, Icon.of(icon == null ? Material.CHEST : icon,
                Design.title(Design.Accent.COMMUNITY, "Category: " + category.display()),
                Design.lore("Where the listing is shelved.", null, "Click to cycle.")), cycleCategory());

        if (price > 0) {
            set(16, d.confirm("List for sale"), confirm());
        } else {
            set(16, Icon.of(Material.BARRIER, Design.OUT + "" + ChatColor.BOLD + "Set a price first",
                    Design.lore("A buy price is required.", null, null)), null);
        }

        set(22, d.cancel(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private String valueOr(double value, String unset) {
        return value > 0 ? ctx.format().format(value) : unset;
    }

    private Consumer<Player> askPrice() {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, "community.list.price-prompt");
                ctx.prompt().await(player, new Consumer<String>() {
                    @Override
                    public void accept(String input) {
                        Double value = parse(input);
                        double next = value != null && value > 0 ? value : price;
                        if (value == null) {
                            ctx.messages().send(player, "community.list.price-invalid");
                        }
                        reopen(player, next, buyBack, category);
                    }
                });
            }
        };
    }

    private Consumer<Player> askBuyBack() {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, "community.list.buyback-prompt");
                ctx.prompt().await(player, new Consumer<String>() {
                    @Override
                    public void accept(String input) {
                        if (input != null && (input.equalsIgnoreCase("none") || input.trim().equals("0"))) {
                            reopen(player, price, ShopItem.NOT_OFFERED, category);
                            return;
                        }
                        Double value = parse(input);
                        reopen(player, price, value != null && value > 0 ? value : buyBack, category);
                    }
                });
            }
        };
    }

    private Consumer<Player> cycleCategory() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                Category[] all = Category.values();
                Category next = all[(category.ordinal() + 1) % all.length];
                reopen(player, price, buyBack, next);
            }
        };
    }

    private Consumer<Player> confirm() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                ShopItem listing = ctx.market().list(player, shop, unit, amount, price, buyBack, category);
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        };
    }

    private void reopen(Player player, double price, double buyBack, Category category) {
        new ListingBuilderMenu(ctx, shop, viewer, unit, amount, price, buyBack, category).open(player);
    }

    private Double parse(String raw) {
        if (raw == null || raw.equalsIgnoreCase("cancel")) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? Double.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
