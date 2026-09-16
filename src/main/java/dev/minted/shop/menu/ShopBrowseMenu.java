package dev.minted.shop.menu;

import dev.minted.gui.Menu;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;

import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Shown by {@code /eshop} when more than one shop exists: each shop's icon,
 * click to open. Sized to fit the shops, capped at six rows.
 */
public final class ShopBrowseMenu extends Menu {

    private final ShopContext ctx;

    public ShopBrowseMenu(ShopContext ctx) {
        super(ctx.messages().get("browse.title"), rows(ctx));
        this.ctx = ctx;
    }

    private static int rows(ShopContext ctx) {
        int count = ctx.shops().all().size();
        int rows = (count + 8) / 9;
        return Math.max(1, Math.min(6, rows));
    }

    @Override
    protected void build() {
        int slot = 0;
        int size = getInventory().getSize();
        for (Shop shop : ctx.shops().all()) {
            if (slot >= size) {
                return;
            }
            set(slot, icon(shop), open(shop));
            slot++;
        }
        fillEmpty(ctx.design().filler());
    }

    private org.bukkit.inventory.ItemStack icon(Shop shop) {
        return Display.withLore(shop.getIcon(), Arrays.asList(
                ctx.messages().get("menu.title", "shop", shop.getName(), "currency", shop.getCurrency().display()),
                ctx.messages().get("browse.open-lore")));
    }

    private Consumer<Player> open(final Shop shop) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopMenu(ctx, shop, 0, null).open(player);
            }
        };
    }
}
