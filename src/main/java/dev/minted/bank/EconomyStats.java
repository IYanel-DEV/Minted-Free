package dev.minted.bank;

import dev.minted.backend.StatsDao;

import org.bukkit.plugin.Plugin;

/**
 * The server-wide economy view shown in the stats menu: how much money sits in
 * bank accounts across everyone, and how much has been burned (left the economy
 * for good through digital shop purchases). Physical cash is deliberately never
 * counted here - a burned note on the ground or in a player's pocket is not part
 * of any tracked pool.
 *
 * <p>Whatever the menu reads comes from this class's cache. The cache is kept
 * fresh by {@link #refresh()}, which recomputes the bank total from storage
 * (corrected to live balances of online accounts) and persists any newly burned
 * money; runs off the main thread on a timer.
 */
public final class EconomyStats {

    private final Plugin plugin;
    private final EconomyService bankEconomy;
    private final StatsDao dao;

    private final Object lock = new Object();
    private volatile double banked;
    private volatile int accounts;
    private double burnedCache;
    private volatile double burned;

    public EconomyStats(Plugin plugin, EconomyService bankEconomy, StatsDao dao) {
        this.plugin = plugin;
        this.bankEconomy = bankEconomy;
        this.dao = dao;
    }

    /** Total money stored in the bank across all players. */
    public double banked() {
        return banked;
    }

    /** Number of bank accounts holding money. */
    public int accountCount() {
        return accounts;
    }

    /** Money that left the economy for good. */
    public double burned() {
        return burned;
    }

    /**
     * Records money destroyed by a digital purchase. Physical-cash spends are
     * not reported here by the caller, matching the "physical cash is uncounted"
     * contract. Safe on the main thread; persisted on the next refresh.
     */
    public void burn(double amount) {
        if (amount <= 0) {
            return;
        }
        synchronized (lock) {
            burnedCache += amount;
            burned = burnedCache;
        }
    }

    /** Loads the persisted burned total; call once when storage is open. */
    public void load() {
        scheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Double stored = dao.load("burned");
                synchronized (lock) {
                    burnedCache = stored != null ? stored : 0;
                    burned = burnedCache;
                }
            }
        });
    }

    /** Recomputes the bank total and flushes the burned counter. Async. */
    public void refresh() {
        scheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                banked = bankEconomy.liveSum();
                accounts = bankEconomy.countAccounts();
                double snapshot;
                synchronized (lock) {
                    snapshot = burnedCache;
                }
                dao.save("burned", snapshot);
            }
        });
    }

    private org.bukkit.scheduler.BukkitScheduler scheduler() {
        return plugin.getServer().getScheduler();
    }
}