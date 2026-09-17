package dev.minted.bank;

import dev.minted.backend.StorageProvider;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Owns the in-memory account cache and drives all persistence off the main
 * thread. Loads happen on join, saves are batched by {@link AccountSaveTask};
 * this class never blocks the server thread on storage except during the
 * shutdown flush, when the scheduler is already gone.
 */
public final class EconomyService {

    private final Plugin plugin;
    private final StorageProvider storage;
    private final double startingBalance;
    private final double maxBalance;

    private final Map<UUID, BankAccount> accounts = new ConcurrentHashMap<UUID, BankAccount>();
    private volatile boolean ready;

    public EconomyService(Plugin plugin, StorageProvider storage, double startingBalance, double maxBalance) {
        this.plugin = plugin;
        this.storage = storage;
        this.startingBalance = startingBalance;
        this.maxBalance = maxBalance;
    }

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        this.ready = true;
    }

    public BankAccount getCached(UUID uuid) {
        return accounts.get(uuid);
    }

    Map<UUID, BankAccount> cache() {
        return accounts;
    }

    /**
     * Total stored balance across the whole table, corrected to the live
     * in-memory balances of any accounts currently cached, so the global view
     * reflects an online player's latest deposit the moment it happens even
     * though the batch saver may not have written it to disk yet.
     *
     * <p>Blocking (talks to storage); call from an async task only.
     */
    public double liveSum() {
        double total = storage.sumBalances();
        for (BankAccount account : accounts.values()) {
            Double stored = storage.loadBalance(account.getUuid());
            total += account.getBalance() - (stored != null ? stored : startingBalance);
        }
        return total;
    }

    /** Blocking; call from an async task only. */
    public int countAccounts() {
        return storage.countAccounts();
    }

    /** Loads an account into the cache, then runs the callback on the main thread. */
    public void load(final UUID uuid, final Consumer<BankAccount> callback) {
        if (accounts.containsKey(uuid)) {
            if (callback != null) {
                callback.accept(accounts.get(uuid));
            }
            return;
        }
        scheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Double stored = storage.loadBalance(uuid);
                final BankAccount account = adopt(uuid, stored);
                if (callback != null) {
                    scheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            callback.accept(account);
                        }
                    });
                }
            }
        });
    }

    /** Saves the account asynchronously, then drops it from the cache. */
    public void unload(final UUID uuid) {
        final BankAccount account = accounts.remove(uuid);
        if (account == null) {
            return;
        }
        scheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                storage.saveBalance(uuid, account.getBalance());
            }
        });
    }

    /**
     * Moves funds between two loaded accounts atomically. Both accounts are
     * locked in UUID order so concurrent transfers can never deadlock.
     *
     * @return true on success; false if either account is unloaded, the amount
     *         is not positive, funds are short, or the target would exceed the cap
     */
    public boolean transfer(BankAccount from, BankAccount to, double amount) {
        if (from == null || to == null || amount <= 0) {
            return false;
        }
        BankAccount first = from.getUuid().compareTo(to.getUuid()) <= 0 ? from : to;
        BankAccount second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!from.withdraw(amount)) {
                    return false;
                }
                if (!to.deposit(amount)) {
                    from.deposit(amount);
                    return false;
                }
                return true;
            }
        }
    }

    /** Flushes every cached balance synchronously; only for {@code onDisable}. */
    public void saveAllBlocking() {
        Map<UUID, Double> snapshot = new HashMap<UUID, Double>();
        for (BankAccount account : accounts.values()) {
            snapshot.put(account.getUuid(), account.getBalance());
        }
        if (!snapshot.isEmpty()) {
            storage.saveBalances(snapshot);
        }
    }

    private BankAccount adopt(UUID uuid, Double stored) {
        double balance = stored != null ? stored : startingBalance;
        BankAccount account = new BankAccount(uuid, balance, maxBalance);
        // A brand-new account has nothing on disk yet; flag it so the first
        // batch save writes the starting balance.
        if (stored == null) {
            account.markDirty();
        }
        BankAccount existing = accounts.putIfAbsent(uuid, account);
        return existing != null ? existing : account;
    }

    private BukkitScheduler scheduler() {
        return plugin.getServer().getScheduler();
    }
}
