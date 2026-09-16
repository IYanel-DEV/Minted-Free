package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * The one-item view: the item in the middle with Buy and Sell buttons. Each
 * button opens a {@link QuantityMenu}; the trade itself runs in {@code Trade},
 * which reports the outcome. A side of the trade the shop does not offer is
 * shown greyed out with no action.
 */
public final class ItemDetailMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final ShopItem item;
    private final int returnPage;

    public ItemDetailMenu(ShopContext ctx, Shop shop, ShopItem item, int returnPage) {
        super(ctx.messages().get("detail.title", "item", shop.getName()), 3);
        this.ctx = ctx;
        this.shop = shop;
        this.item = item;
        this.returnPage = returnPage;
    }

    @Override
    protected void build() {
        set(13, item.copy(), null);

        if (item.isBuyable()) {
            set(11, Icon.of(Material.GOLD_INGOT, ctx.messages().get("detail.buy"),
                            ctx.messages().get("detail.buy-lore", "price", ctx.format().format(item.getBuyPrice()))),
                    openQuantity(ctx.messages().get("detail.buy"), true));
        } else {
            set(11, Icon.of(Material.GOLD_INGOT, ctx.messages().get("item.not-buyable")), null);
        }

        if (item.isSellable() && item.getSellPrice() > 0) {
            set(15, Icon.of(Material.EMERALD, ctx.messages().get("detail.sell"),
                            ctx.messages().get("detail.sell-lore", "price", ctx.format().format(item.getSellPrice()))),
                    openQuantity(ctx.messages().get("detail.sell"), false));
        } else {
            set(15, Icon.of(Material.EMERALD, ctx.messages().get("item.not-sellable")), null);
        }

        set(22, Icon.of(Material.ARROW, ctx.messages().get("menu.back")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopMenu(ctx, shop, returnPage, null).open(player);
            }
        });
    }

    private Consumer<Player> openQuantity(final String action, final boolean buying) {
        final String title = ctx.messages().get("quantity.title", "action", action);
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new QuantityMenu(ctx, title, trade(buying)).open(player);
            }
        };
    }

    private QuantityMenu.QuantityChoice trade(final boolean buying) {
        return new QuantityMenu.QuantityChoice() {
            @Override
            public void chosen(Player player, int quantity) {
                if (buying) {
                    ctx.trade().buy(player, shop, item, quantity);
                } else {
                    ctx.trade().sell(player, shop, item, quantity);
                }
                new ItemDetailMenu(ctx, shop, item, returnPage).open(player);
            }
        };
    }
}
