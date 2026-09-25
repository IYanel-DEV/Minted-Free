package dev.minted.shop.menu;

import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.catalog.Category;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers where each player left off while browsing shops, so the {@code /sh}
 * and {@code /psh} shortcuts drop them straight back into the last catalog page,
 * category or search instead of making them navigate from the front door again.
 * Kept in memory only - a player rejoining starts clean, exactly as if they had
 * not browsed yet.
 *
 * <p>Two slots per player, one for each side of the shop: the server's own
 * catalog (what {@code /shop} shows) and player storefronts (what {@code /pshop}
 * shows). A view is a shop plus the grid showing it - the records keep enough
 * to rebuild that exact grid, or the shop's front page when that is all the
 * player reached.
 */
public final class BrowseHistory {

    /** One remembered view: a shop plus the browse state that was open on it. */
    public static final class Bookmark {

        private final String shopName;
        private final boolean playerShop;
        private final boolean home;
        private final Category category;
        private final String query;
        private final Sort sort;
        private final int page;

        Bookmark(String shopName, boolean playerShop, boolean home,
                 Category category, String query, Sort sort, int page) {
            this.shopName = shopName;
            this.playerShop = playerShop;
            this.home = home;
            this.category = category;
            this.query = query;
            this.sort = sort;
            this.page = page;
        }

        public Sort sort() {
            return sort;
        }

        /**
         * Reopens the remembered view, or does nothing when the shop it points
         * at no longer exists - or changed sides (a server catalog turned into
         * a player storefront, or vice versa).
         *
         * @return true when a menu opened, false when the view is stale.
         */
        public boolean open(Player viewer, ShopContext ctx) {
            Shop shop = ctx.shops().get(shopName);
            if (shop == null || isPlayerShop(shop) != playerShop) {
                return false;
            }
            if (home) {
                new HomeMenu(ctx, shop, viewer).open(viewer);
            } else {
                new GridMenu(ctx, shop, viewer, category, query, sort, page).open(viewer);
            }
            return true;
        }
    }

    private final Map<UUID, Bookmark> globalBookmarks = new HashMap<UUID, Bookmark>();
    private final Map<UUID, Bookmark> playerBookmarks = new HashMap<UUID, Bookmark>();

    private static boolean isPlayerShop(Shop shop) {
        return shop.getOwner() != null || shop.isPlayerShop();
    }

    /**
     * Remembers the browse grid currently open for this player: a category, a
     * search result or the whole catalog. Player-owned storefronts never mix
     * with the server catalog - they are recorded in their own slot.
     */
    public void record(Player viewer, Shop shop, Category category, String query, Sort sort, int page) {
        record(viewer, shop, new Bookmark(shop.getName(), isPlayerShop(shop), false,
                category, query, sort, page));
    }

    /** Remembers that the player only reached the shop's front page. */
    public void recordHome(Player viewer, Shop shop) {
        record(viewer, shop, new Bookmark(shop.getName(), isPlayerShop(shop), true,
                null, null, Sort.NONE, 0));
    }

    private void record(Player viewer, Shop shop, Bookmark bookmark) {
        if (bookmark.playerShop) {
            playerBookmarks.put(viewer.getUniqueId(), bookmark);
        } else {
            globalBookmarks.put(viewer.getUniqueId(), bookmark);
        }
    }

    /** The last view this player had on the given side of the shop, or null. */
    public Bookmark bookmark(UUID uuid, boolean playerShop) {
        return playerShop ? playerBookmarks.get(uuid) : globalBookmarks.get(uuid);
    }

    /**
     * Reopens the last view this player had, on the side of the shop the given
     * command serves.
     *
     * @return true when a bookmarked view opened, false when there was none or
     *         the shop it pointed at is gone.
     */
    public boolean openLast(Player viewer, ShopContext ctx, boolean playerShop) {
        Bookmark bookmark = bookmark(viewer.getUniqueId(), playerShop);
        return bookmark != null && bookmark.open(viewer, ctx);
    }
}