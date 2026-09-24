package dev.minted.shop;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.shop.storage.ShopDao;
import dev.minted.shop.storage.ShopItemRow;
import dev.minted.shop.storage.ShopRow;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Owns the in-memory shop model and keeps it in step with the database. Shops
 * and their items are read once at startup (and again on {@link #reload()}),
 * assembled on the main thread, and served from memory thereafter. Every write
 * changes memory immediately and is mirrored to the database on an async task,
 * matching the accounts layer: the main thread never blocks on storage.
 */
public final class ShopService {

    private final Plugin plugin;
    private final ShopDao dao;
    private final ServerVersion version;
    private final MaterialLookup materials;

    // Keyed by lower-cased name; insertion order gives the browse order and the
    // "first shop is the default" rule.
    private final Map<String, Shop> shops = new LinkedHashMap<String, Shop>();

    // A single writer thread runs all reads and writes in submission order, so an
    // item never reaches the database before the shop it belongs to (which a
    // foreign-key backend like MySQL would reject).
    private final ExecutorService writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Minted-Shop-Writer");
            thread.setDaemon(true);
            return thread;
        }
    });

    private volatile boolean ready;
    private int nextId = 1;

    // Optional lifecycle callbacks (the VIP /<username> shop commands).
    // Set once during startup; every fire happens on the main thread.
    private PlayerShopHooks playerShopHooks;

    public ShopService(Plugin plugin, ShopDao dao, ServerVersion version, MaterialLookup materials) {
        this.plugin = plugin;
        this.dao = dao;
        this.version = version;
        this.materials = materials;
    }

    public MaterialLookup materials() {
        return materials;
    }

    /** A startup/reload line the seeder can use to report catalog growth. */
    void info(String message) {
        plugin.getLogger().info(message);
    }

    /** A startup/reload warning, e.g. catalog rows this server could not resolve. */
    void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public boolean isReady() {
        return ready;
    }

    /** Attaches the player-shop lifecycle callbacks; call before initialize(). */
    public void setPlayerShopHooks(PlayerShopHooks hooks) {
        this.playerShopHooks = hooks;
    }

    /** First load: reads the DB off-thread, builds on the main thread, seeds if empty. */
    public void initialize() {
        loadAsync(true);
    }

    /** In-game reload: re-reads every shop, replacing the model without a restart. */
    public void reload() {
        loadAsync(false);
    }

    private void loadAsync(final boolean seedIfEmpty) {
        writer.execute(new Runnable() {
            @Override
            public void run() {
                final List<ShopRow> shopRows;
                final List<ShopItemRow> itemRows;
                try {
                    dao.createTables();
                    shopRows = dao.loadShops();
                    itemRows = dao.loadItems();
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Minted could not load shops: " + e.getMessage());
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        build(shopRows, itemRows, seedIfEmpty);
                    }
                });
            }
        });
    }

    private void build(List<ShopRow> shopRows, List<ShopItemRow> itemRows, boolean seedIfEmpty) {
        ShopModelBuilder.Result loaded = new ShopModelBuilder(plugin.getLogger()).build(shopRows, itemRows);
        shops.clear();
        shops.putAll(loaded.shops);
        nextId = loaded.nextId;
        ready = true;
        if (seedIfEmpty) {
            // Seeds the global starter only on a truly empty install, and the
            // community marketplace whenever it is missing (covers an upgrade
            // from a pre-0.10.0 database that already has global shops). The
            // growth step also runs on reload: it is idempotent, so a fresh
            // jar grows every global shop the moment it is loaded.
            ShopSeeder.seed(this, version, materials, shops.isEmpty());
        } else {
            ShopSeeder.seed(this, version, materials, false);
        }
        if (playerShopHooks != null) {
            playerShopHooks.onShopsLoaded();
        }
    }

    public Shop get(String name) {
        return name == null ? null : shops.get(key(name));
    }

    public boolean exists(String name) {
        return name != null && shops.containsKey(key(name));
    }

    public Collection<Shop> all() {
        return new ArrayList<Shop>(shops.values());
    }

    public Shop first() {
        return shops.isEmpty() ? null : shops.values().iterator().next();
    }

    private Shop byId(int id) {
        for (Shop shop : shops.values()) {
            if (shop.getId() == id) {
                return shop;
            }
        }
        return null;
    }

    public Shop create(String name, ItemStack icon, Currency currency) {
        return create(name, icon, currency, ShopType.GLOBAL, null);
    }

    public Shop create(String name, ItemStack icon, Currency currency, ShopType type) {
        return create(name, icon, currency, type, null);
    }

    public Shop create(String name, ItemStack icon, Currency currency, ShopType type, UUID owner) {
        final int id = nextId++;
        Shop shop = new Shop(id, name, icon, currency, type, owner);
        shops.put(key(name), shop);
        final String iconData = ItemCodec.encode(icon);
        final String currencyId = currency.id();
        final String stored = name;
        final String typeId = type.id();
        final String storedOwner = owner == null ? null : owner.toString();
        async(new Runnable() {
            @Override
            public void run() {
                dao.insertShop(id, stored, iconData, currencyId, typeId, storedOwner);
            }
        });
        if (type == ShopType.PLAYER && playerShopHooks != null) {
            playerShopHooks.onPlayerShopCreated(shop);
        }
        return shop;
    }

    /** Opens a personal storefront for a player, always trading in the wallet. */
    public Shop createPlayerShop(String name, ItemStack icon, UUID owner) {
        return create(name, icon, Currency.WALLET, ShopType.PLAYER, owner);
    }

    /** Every player shop owned by the given player, in creation order. */
    public Collection<Shop> shopsFor(UUID owner) {
        List<Shop> owned = new ArrayList<Shop>();
        for (Shop shop : shops.values()) {
            if (shop.isPlayerShop() && shop.ownedBy(owner)) {
                owned.add(shop);
            }
        }
        return owned;
    }

    /** The first player shop owned by the player, or null. */
    public Shop playerShopOf(UUID owner) {
        for (Shop shop : shops.values()) {
            if (shop.isPlayerShop() && shop.ownedBy(owner)) {
                return shop;
            }
        }
        return null;
    }

    public int playerShopCount(UUID owner) {
        return shopsFor(owner).size();
    }

    /** The single community marketplace, or null before it is seeded. */
    public Shop community() {
        for (Shop shop : shops.values()) {
            if (shop.isCommunity()) {
                return shop;
            }
        }
        return null;
    }

    public void delete(Shop shop) {
        shops.remove(key(shop.getName()));
        final int id = shop.getId();
        async(new Runnable() {
            @Override
            public void run() {
                dao.deleteShop(id);
            }
        });
        if (shop.isPlayerShop() && playerShopHooks != null) {
            playerShopHooks.onPlayerShopDeleted(shop);
        }
    }

    public void rename(Shop shop, String newName) {
        shops.remove(key(shop.getName()));
        shop.setName(newName);
        shops.put(key(newName), shop);
        saveMeta(shop);
    }

    public void setIcon(Shop shop, ItemStack icon) {
        shop.setIcon(icon);
        saveMeta(shop);
    }

    public void setCurrency(Shop shop, Currency currency) {
        shop.setCurrency(currency);
        saveMeta(shop);
    }

    private void saveMeta(Shop shop) {
        final int id = shop.getId();
        final String name = shop.getName();
        final String iconData = ItemCodec.encode(shop.getIcon());
        final String currencyId = shop.getCurrency().id();
        async(new Runnable() {
            @Override
            public void run() {
                dao.updateShop(id, name, iconData, currencyId);
            }
        });
    }

    /** Persists an item just placed or changed in memory at its page and slot. */
    public void saveItem(Shop shop, ShopItem item) {
        shop.put(item);
        final int id = shop.getId();
        final int page = item.getPage();
        final int slot = item.getSlot();
        final String data = ItemCodec.encode(item.copy());
        final double buy = item.getBuyPrice();
        final double sell = item.getSellPrice();
        final String category = item.getCategory();
        final String owner = item.getOwner() == null ? null : item.getOwner().toString();
        final long stock = item.getStock();
        final double buyBack = item.getBuyBackPrice();
        final double earnings = item.getEarnings();
        async(new Runnable() {
            @Override
            public void run() {
                dao.saveItem(id, page, slot, data, buy, sell, category, owner, stock, buyBack, earnings);
            }
        });
    }

    public void removeItem(Shop shop, final int page, final int slot) {
        shop.removeAt(page, slot);
        final int id = shop.getId();
        async(new Runnable() {
            @Override
            public void run() {
                dao.deleteItem(id, page, slot);
            }
        });
    }

    private void async(Runnable task) {
        try {
            writer.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            // The writer is already stopping (server shutdown mid-seed). A lost
            // write is fine: the seed re-runs next boot, and it beats crashing.
        }
    }

    /**
     * Stops the writer, waiting for queued saves to finish before the pool that
     * backs them is closed. The window is generous because a fresh-install seed
     * can enqueue dozens of writes; a normal shutdown drains in well under it.
     */
    public void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
