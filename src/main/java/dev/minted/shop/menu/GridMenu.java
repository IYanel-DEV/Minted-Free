package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;
import dev.minted.shop.catalog.Category;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A filtered, sorted, paginated grid of a shop's items - one category, a search
 * result, or everything. The sort button cycles price/name and shows the active
 * mode in its lore; the search button reopens the chat prompt. Global items keep
 * the v0.8.0 quick-trade clicks; a community listing opens {@link ListingMenu}.
 */
public final class GridMenu extends Menu {

    private static final int PAGE_SIZE = 28;

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;
    private final Category category;
    private final String query;
    private final Sort sort;
    private final int page;

    public GridMenu(ShopContext ctx, Shop shop, Player viewer, Category category,
                    String query, Sort sort, int page) {
        super(Design.title(accent(shop), heading(category, query)), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
        this.category = category;
        this.query = query;
        this.sort = sort;
        this.page = page;
    }

    private static Design.Accent accent(Shop shop) {
        return shop.isCommunity() ? Design.Accent.COMMUNITY
                : shop.isPlayerShop() ? Design.Accent.SHOP : Design.Accent.SHOP;
    }

    private static String heading(Category category, String query) {
        if (query != null && !query.isEmpty()) {
            return "Search";
        }
        return category == null ? "All items" : category.display();
    }

    @Override
    protected void build() {
        ctx.history().record(viewer, shop, category, query, sort, page);
        Design d = ctx.design();
        frame(d.border(accent(shop)));

        List<ShopItem> items = view();
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int current = ((page % pages) + pages) % pages;

        List<Integer> slots = interiorSlots();
        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            setClick(slots.get(i), describe(item), click(item));
        }

        set(45, d.back(), toHome());
        set(46, d.prev(), openPage(current - 1, pages));
        set(47, d.pageInfo(current + 1, pages), null);
        set(48, d.next(), openPage(current + 1, pages));
        
        // Single price sort toggle button
        set(50, priceSortToggle(), togglePriceSort());
        
        set(52, d.search(query), reSearch());
        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });
        fillEmpty(d.filler());
    }

    /**
     * Creates a single price sort toggle button.
     * Shows current mode, toggles between Low→High and High→Low on click.
     * Yellow = active/enabled, Gray = inactive.
     */
    private ItemStack priceSortToggle() {
        boolean isLow = sort == Sort.PRICE_LOW;
        boolean isHigh = sort == Sort.PRICE_HIGH;
        
        String label = isLow ? "Low → High" : "High → Low";
        String nextLabel = isLow ? "High → Low" : "Low → High";
        
        // Yellow when active (either low or high), gray when default/none
        boolean active = isLow || isHigh;
        Material mat = active ? Material.GOLD_INGOT : Material.INK_SACK;
        ChatColor nameColor = active ? ChatColor.YELLOW : ChatColor.GRAY;
        
        List<String> lore = new ArrayList<String>();
        lore.add(Design.HINT + "Current: " + ChatColor.WHITE + label);
        lore.add(Design.HINT + "Click to toggle: " + ChatColor.WHITE + nextLabel);
        if (active) {
            lore.add("");
            lore.add(Design.IN + "" + ChatColor.BOLD + "► ENABLED ◄");
        } else {
            lore.add("");
            lore.add(Design.HINT + "Default (shop order)");
        }
        return Icon.of(mat, nameColor + "" + ChatColor.BOLD + label, lore.toArray(new String[0]));
    }

    /** Toggles between PRICE_LOW, PRICE_HIGH, and NONE (default). */
    private Consumer<Player> togglePriceSort() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                Sort next;
                if (sort == Sort.PRICE_LOW) {
                    next = Sort.PRICE_HIGH;
                } else if (sort == Sort.PRICE_HIGH) {
                    next = Sort.NONE;
                } else {
                    next = Sort.PRICE_LOW;
                }
                new GridMenu(ctx, shop, viewer, category, query, next, 0).open(player);
            }
        };
    }

    private List<ShopItem> view() {
        List<ShopItem> result = new ArrayList<ShopItem>();
        boolean stockTracked = shop.isCommunity() || shop.isPlayerShop();
        for (ShopItem item : shop.allItems()) {
            if (stockTracked && item.getStock() <= 0) {
                continue;
            }
            if (category != null && Category.byKey(item.getCategory()) != category) {
                continue;
            }
            if (query != null && !matches(item)) {
                continue;
            }
            result.add(item);
        }
        if (sort.comparator() != null) {
            Collections.sort(result, sort.comparator());
        }
        return result;
    }

    private boolean matches(ShopItem item) {
        String needle = query.toLowerCase(Locale.ROOT);
        String material = item.copy().getType().name().toLowerCase(Locale.ROOT);
        return Sort.label(item).contains(needle) || material.contains(needle);
    }

    private ItemStack describe(ShopItem item) {
        List<String> lore = new ArrayList<String>();
        if (shop.isCommunity() || item.getOwner() != null) {
            lore.add(Design.MONEY + "Buy: " + ChatColor.WHITE + ctx.format().format(item.getBuyPrice()) + " each");
            lore.add(Design.HINT + "In stock: " + ChatColor.WHITE + item.getStock());
            lore.add(Design.HINT + "Seller: " + ChatColor.WHITE + ownerName(item.getOwner()));
            if (item.buysBack()) {
                lore.add(Design.IN + "Buys back: " + ChatColor.WHITE + ctx.format().format(item.getBuyBackPrice()));
            }
            lore.add("");
            lore.add(Design.HINT + "Click to trade.");
        } else {
            if (item.isBuyable()) {
                lore.add(Design.MONEY + "Buy: " + ChatColor.WHITE + ctx.format().format(item.getBuyPrice()));
            }
            if (item.isSellable() && item.getSellPrice() > 0) {
                lore.add(Design.IN + "Sell: " + ChatColor.WHITE + ctx.format().format(item.getSellPrice()));
            }
            lore.add("");
            lore.add(Design.HINT + "Left buy | Right sell | Shift-left details");
        }
        return Display.withLore(item.copy(), lore);
    }

    private ClickHandler click(final ShopItem item) {
        return new ClickHandler() {
            @Override
            public void click(Player player, ClickType type) {
                // Community listings and any item owned by a player (a player
                // shop's whole stock) trade through the market, not the void.
                if (shop.isCommunity() || item.getOwner() != null) {
                    new ListingMenu(ctx, shop, viewer, item).open(player);
                    return;
                }
                if (type == ClickType.SHIFT_LEFT) {
                    new ItemDetailMenu(ctx, shop, item, 0, viewer).open(player);
                    return;
                }
                if (type == ClickType.SHIFT_RIGHT || type == ClickType.MIDDLE) {
                    ctx.trade().sell(player, shop, item, Integer.MAX_VALUE);
                } else if (type.isRightClick()) {
                    ctx.trade().sellOne(player, shop, item);
                } else if (type.isLeftClick()) {
                    ctx.trade().buy(player, shop, item, 1);
                }
                reopen(player);
            }
        };
    }

    private String ownerName(UUID owner) {
        if (owner == null) {
            return "a player";
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
        return offline.getName() == null ? "a player" : offline.getName();
    }

    private List<Integer> interiorSlots() {
        List<Integer> slots = new ArrayList<Integer>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private Consumer<Player> toHome() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        };
    }

    private Consumer<Player> openPage(final int target, final int pages) {
        final int wrapped = ((target % pages) + pages) % pages;
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new GridMenu(ctx, shop, viewer, category, query, sort, wrapped).open(player);
            }
        };
    }

    private Consumer<Player> cycleSort() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new GridMenu(ctx, shop, viewer, category, query, sort.nextMode(), page).open(player);
            }
        };
    }

    private Consumer<Player> setSortLow() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new GridMenu(ctx, shop, viewer, category, query, Sort.PRICE_LOW, 0).open(player);
            }
        };
    }

    private Consumer<Player> setSortHigh() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new GridMenu(ctx, shop, viewer, category, query, Sort.PRICE_HIGH, 0).open(player);
            }
        };
    }

    private Consumer<Player> reSearch() {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, "community.search.prompt");
                ctx.prompt().await(player, new Consumer<String>() {
                    @Override
                    public void accept(String input) {
                        if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                            new HomeMenu(ctx, shop, viewer).open(player);
                            return;
                        }
                        new GridMenu(ctx, shop, viewer, null, input.trim(), sort, 0).open(player);
                    }
                });
            }
        };
    }

    private void reopen(Player player) {
        new GridMenu(ctx, shop, viewer, category, query, sort, page).open(player);
    }
}
