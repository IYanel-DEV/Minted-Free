package dev.minted.shop.log;

import dev.minted.backend.SaleDao;

import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * The server-wide sales feed. Every successful sale is recorded here (shop and
 * marketplace alike) off the main thread; the feed itself is read through
 * {@link #recent} by an async caller, typically a menu. The id counter and the
 * occasional prune keep the table bounded forever.
 */
public final class SaleLog {

    private final Plugin plugin;
    private final SaleDao dao;

    private volatile int nextId = 1;

    public SaleLog(Plugin plugin, SaleDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    /** Creates the table and resumes the id counter. Blocking; async caller only. */
    public void initialize() {
        dao.createTable();
        nextId = dao.maxId() + 1;
    }

    /**
     * Records a sale. Never blocks the caller and never throws: the feed is
     * cosmetic, so a storage stumble loses a row rather than a trade.
     *
     * @param kind   "shop" or "market"
     * @param seller the selling player, or null when the shop sold it
     * @param buyer  the buying player, or null when the shop bought it
     */
    public void record(String kind, UUID seller, UUID buyer, String item, int qty, double price) {
        final int id = nextId++;
        final long ts = System.currentTimeMillis();
        final String sellerId = seller != null ? seller.toString() : "";
        final String buyerId = buyer != null ? buyer.toString() : "";
        final String captured = item == null ? "item" : item;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.insert(id, ts, sellerId, buyerId, captured, qty, price, kind);
                    if (id % 32 == 0) {
                        dao.prune(400);
                    }
                } catch (RuntimeException ignored) {
                    // A lost sales row is tolerable; never break the trade path.
                }
            }
        });
    }

    /** Blocking; call from an async task only. Newest first. */
    public java.util.List<SaleEntry> recent(int limit) {
        return dao.recent(limit);
    }
}