package dev.minted.shop;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.shop.storage.GlobalShopFile;
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
 * Owns the in-memory shop model and keeps it in step with storage. Admin global
 * shops live in {@code global-shops.yml} ({@link GlobalShopFile} - version-safe
 * item data, editable and regenerable by a website), while community and player
 * shops keep their real stock and ownership in the database. Everything is read
 * once at startup (and again on {@link #reload()}), assembled on the main
 * thread, and served from memory thereafter. Every write changes memory
 * immediately and is mirrored to its storage on an async task, matching the
 * accounts layer: the main thread never blocks on storage.
 */
public final class ShopService {

    private final Plugin plugin;
    private final ShopDao dao;
    private final ServerVersion version;
    private final MaterialLookup materials;
    private final GlobalShopFile file;

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
        this.file = new GlobalShopFile(plugin.getDataFolder(), version, materials, plugin.getLogger());
    }

    public MaterialLookup materials() {
        return materials;
    }

    /** The plugin instance, for config access. */
    public Plugin getPlugin() {
        return plugin;
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
        loadAsync();
    }

    /** In-game reload: re-reads every shop, replacing the model without a restart. */
    public void reload() {
        loadAsync();
    }

    private void loadAsync() {
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
                        build(shopRows, itemRows);
                    }
                });
            }
        });
    }

    private void build(List<ShopRow> shopRows, List<ShopItemRow> itemRows) {
        ShopModelBuilder.Result loaded = new ShopModelBuilder(plugin.getLogger()).build(shopRows, itemRows);
        shops.clear();
        shops.putAll(loaded.shops);
        nextId = loaded.nextId;

        // Global shops now live in the file, not the database. Any admin row
        // (GLOBAL without an owner) still waiting in the DB is a legacy flight:
        // on a missing file it is exported to disk (one-time migration) or, when
        // there is nothing to export, the default catalog file is seeded. The
        // export is the gate: the database rows are only dropped once the file
        // actually hit the disk, so an unwritable data folder can never lose an
        // admin's shops - the migration then aborts and the legacy rows keep
        // serving live instead.
        List<Shop> legacyGlobals = new ArrayList<Shop>();
        for (Shop shop : shops.values()) {
            if (isFileBacked(shop)) {
                legacyGlobals.add(shop);
            }
        }
        boolean migrated = false;
        if (!file.exists()) {
            if (legacyGlobals.isEmpty()) {
                file.writeDefault();
                plugin.getLogger().info("Seeded the global shop file '" + file.fileName()
                        + "' with the default catalog for " + version + ".");
            } else {
                final List<Shop> snapshot = new ArrayList<Shop>(legacyGlobals);
                if (file.save(snapshot)) {
                    migrated = true;
                    for (Shop shop : legacyGlobals) {
                        final int id = shop.getId();
                        async(new Runnable() {
                            @Override
                            public void run() {
                                dao.deleteShop(id);
                            }
                        });
                    }
                    plugin.getLogger().info("Moved " + legacyGlobals.size() + " database global shop(s)"
                            + " to '" + file.fileName() + "' (global shops are file-managed from here on).");
                } else {
                    plugin.getLogger().severe("Could not write '" + file.fileName() + "' to migrate the "
                            + legacyGlobals.size() + " database global shop(s). Keeping them running from the"
                            + " database; fix the plugin folder permissions and re-run /eshop reload.");
                }
            }
        }
        if (migrated) {
            for (Shop shop : legacyGlobals) {
                shops.remove(key(shop.getName()));
            }
        }
        // The file is the single source of truth for global shops: load it into
        // memory, then let the catalog grow any shop the file marks for it.
        List<Shop> fromFile = file.load(nextId);
        boolean grewAny = false;
        for (Shop shop : fromFile) {
            shops.put(key(shop.getName()), shop);
            nextId = Math.max(nextId, shop.getId() + 1);
            if (file.wantsGrowth(shop)) {
                file.grow(shop);
                grewAny = true;
            }
        }
        if (grewAny) {
            persistFile();
        }

        ready = true;
        ShopSeeder.seed(this, version, materials);
        // A final persist re-synchronises the file with whatever the load and
        // seed phase settled on (growth, migrated rows), even when a queued
        // write from before the reload lands afterwards.
        persistFile();

        if (playerShopHooks != null) {
            playerShopHooks.onShopsLoaded();
        }
    }

    /** Global shops with no owner are file-managed; everything else is the database. */
    boolean isFileBacked(Shop shop) {
        return shop.getType() == ShopType.GLOBAL && shop.getOwner() == null;
    }

    /**
     * Queues a whole-file rewrite of the current global shops, captured on the
     * calling (main) thread so the writer never iterates the live map.
     */
    private void persistFile() {
        final List<Shop> snapshot = new ArrayList<Shop>();
        for (Shop shop : shops.values()) {
            if (isFileBacked(shop)) {
                snapshot.add(shop);
            }
        }
        async(new Runnable() {
            @Override
            public void run() {
                file.save(snapshot);
            }
        });
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
        if (isFileBacked(shop)) {
            persistFile();
        } else {
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
        }
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

    /**
     * Rewrites which kind of shop this is and who owns it, in memory and in
     * storage. An owner turns it into that player's storefront (and moves it
     * out of the global catalog); no owner makes it a server shop again.
     * Crossing the file/database boundary rewrites the shop's whole backing:
     * a global shop handed to a player leaves the file for a fresh database
     * row (keeping its items), and a storefront returned to the server leaves
     * the database for the file.
     */
    public void setOwnership(Shop shop, UUID owner, ShopType type) {
        boolean wasFile = isFileBacked(shop);
        shop.setOwnership(owner, type);
        boolean nowFile = isFileBacked(shop);
        final int id = shop.getId();
        if (wasFile && !nowFile) {
            file.drop(shop.getName());
            persistFile();
            final String name = shop.getName();
            final String iconData = ItemCodec.encode(shop.getIcon());
            final String currencyId = shop.getCurrency().id();
            final String typeId = type.id();
            final String storedOwner = owner == null ? null : owner.toString();
            final List<ShopItem> items = new ArrayList<ShopItem>(shop.allItems());
            async(new Runnable() {
                @Override
                public void run() {
                    dao.insertShop(id, name, iconData, currencyId, typeId, storedOwner);
                    for (ShopItem item : items) {
                        dao.saveItem(id, item.getPage(), item.getSlot(), ItemCodec.encode(item.copy()),
                                item.getBuyPrice(), item.getSellPrice(), item.getCategory(),
                                storedOwner, item.getStock(), item.getBuyBackPrice(), item.getEarnings());
                    }
                }
            });
        } else if (!wasFile && nowFile) {
            pendingOwnershipDelete(id);
            persistFile();
        } else if (nowFile) {
            persistFile();
        } else {
            pendingOwnershipUpdate(id, type, owner);
        }
    }

    private void pendingOwnershipUpdate(final int id, final ShopType type, final UUID owner) {
        final String typeId = type.id();
        final String storedOwner = owner == null ? null : owner.toString();
        async(new Runnable() {
            @Override
            public void run() {
                dao.updateOwnership(id, typeId, storedOwner);
            }
        });
    }

    private void pendingOwnershipDelete(final int id) {
        async(new Runnable() {
            @Override
            public void run() {
                dao.deleteShop(id);
            }
        });
    }

    public void delete(Shop shop) {
        boolean wasFile = isFileBacked(shop);
        shops.remove(key(shop.getName()));
        if (wasFile) {
            file.drop(shop.getName());
            persistFile();
            return;
        }
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
        String oldKey = key(shop.getName());
        shops.remove(oldKey);
        shop.setName(newName);
        String newKey = key(newName);
        shops.put(newKey, shop);
        if (isFileBacked(shop)) {
            file.remapGrowable(oldKey, newKey);
        }
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
        if (isFileBacked(shop)) {
            persistFile();
            return;
        }
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
        if (isFileBacked(shop)) {
            persistFile();
            return;
        }
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
        if (isFileBacked(shop)) {
            persistFile();
            return;
        }
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
