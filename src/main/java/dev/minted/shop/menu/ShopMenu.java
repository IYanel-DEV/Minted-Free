package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The player's view of a shop: a row of category filters on top, the matching
 * items below, and page controls on the bottom row that wrap past the last
 * page. Items flow into the catalog in placement order; the category filter
 * narrows them to one tag (or the uncategorised ones), so a home page groups
 * cleanly without the admin having to lay the categories out by hand.
 */
public final class ShopMenu extends Menu {

    private static final int ITEM_START = 9;
    private static final int PAGE_SIZE = 36;
    private static final int NAV_PREV = 45;
    private static final int NAV_BACK = 48;
    private static final int NAV_INFO = 49;
    private static final int NAV_NEXT = 53;

    // Distinct from any real category name, so it can flag the uncategorised filter.
    private static final String UNCATEGORISED = "uncategorised";

    private final ShopContext ctx;
    private final Shop shop;
    private final int page;
    private final String filter;

    public ShopMenu(ShopContext ctx, Shop shop, int page, String filter) {
        super(ctx.messages().get("menu.title", "shop", shop.getName(),
                "currency", shop.getCurrency().display()), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.page = page;
        this.filter = filter;
    }

    @Override
    protected void build() {
        List<ShopItem> items = filtered();
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int current = ((page % pages) + pages) % pages;

        renderCategories();
        renderItems(items, current);
        renderNav(pages, current);
    }

    private List<ShopItem> filtered() {
        List<ShopItem> result = new ArrayList<ShopItem>();
        for (ShopItem item : shop.allItems()) {
            if (matches(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean matches(ShopItem item) {
        if (filter == null) {
            return true;
        }
        if (UNCATEGORISED.equals(filter)) {
            return item.getCategory() == null;
        }
        return filter.equals(item.getCategory());
    }

    private void renderCategories() {
        set(0, Icon.of(Material.BOOK, ctx.messages().get("menu.category-all")), openFilter(null));
        int slot = 1;
        for (String category : shop.categories()) {
            if (slot > 8) {
                return;
            }
            set(slot, Icon.of(Material.CHEST, ctx.messages().get("menu.category", "category", category)),
                    openFilter(category));
            slot++;
        }
        if (slot <= 8 && hasUncategorised()) {
            set(slot, Icon.of(Material.PAPER, ctx.messages().get("menu.category-uncategorised")),
                    openFilter(UNCATEGORISED));
        }
    }

    private void renderItems(List<ShopItem> items, int current) {
        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            setClick(ITEM_START + i, describe(item), itemClick(item, current));
        }
    }

    private void renderNav(int pages, final int current) {
        set(NAV_PREV, Icon.of(Material.ARROW, ctx.messages().get("menu.prev-page")), openPage(current - 1, pages));
        set(NAV_INFO, Icon.of(Material.PAPER, ctx.messages().get("menu.page-info",
                "page", String.valueOf(current + 1), "pages", String.valueOf(pages))), null);
        set(NAV_NEXT, Icon.of(Material.ARROW, ctx.messages().get("menu.next-page")), openPage(current + 1, pages));
        if (ctx.shops().all().size() > 1) {
            set(NAV_BACK, Icon.of(Material.CHEST, ctx.messages().get("menu.back")), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    new ShopBrowseMenu(ctx).open(player);
                }
            });
        }
    }

    private ItemStack describe(ShopItem item) {
        List<String> lore = new ArrayList<String>();
        if (item.isBuyable()) {
            lore.add(ctx.messages().get("item.buy-lore", "price", ctx.format().format(item.getBuyPrice())));
        } else {
            lore.add(ctx.messages().get("item.not-buyable"));
        }
        if (item.isSellable() && item.getSellPrice() > 0) {
            lore.add(ctx.messages().get("item.sell-lore", "price", ctx.format().format(item.getSellPrice())));
        } else {
            lore.add(ctx.messages().get("item.not-sellable"));
        }
        lore.add(ctx.messages().get("item.hint"));
        return Display.withLore(item.copy(), lore);
    }

    // Direct trading is the default; only Shift+Left opens the quantity picker.
    // Scroll-wheel-in-GUI sends no packet to any server, so "sell all" is carried
    // by Shift+Right (works on every client) and Middle (sent by pick-block).
    private ClickHandler itemClick(final ShopItem item, final int current) {
        return new ClickHandler() {
            @Override
            public void click(Player player, ClickType type) {
                if (type == ClickType.SHIFT_LEFT) {
                    new ItemDetailMenu(ctx, shop, item, current).open(player);
                    return;
                }
                if (type == ClickType.SHIFT_RIGHT || type == ClickType.MIDDLE) {
                    // Sell-all: Trade clamps this to what the player actually owns,
                    // so we lean on its inventory scan instead of writing our own.
                    ctx.trade().sell(player, shop, item, Integer.MAX_VALUE);
                } else if (type.isRightClick()) {
                    ctx.trade().sellOne(player, shop, item);
                } else if (type.isLeftClick()) {
                    ctx.trade().buy(player, shop, item, 1);
                }
                new ShopMenu(ctx, shop, current, filter).open(player);
            }
        };
    }

    private Consumer<Player> openFilter(final String category) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopMenu(ctx, shop, 0, category).open(player);
            }
        };
    }

    private Consumer<Player> openPage(final int target, final int pages) {
        final int wrapped = ((target % pages) + pages) % pages;
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopMenu(ctx, shop, wrapped, filter).open(player);
            }
        };
    }

    private boolean hasUncategorised() {
        for (ShopItem item : shop.allItems()) {
            if (item.getCategory() == null) {
                return true;
            }
        }
        return false;
    }
}
