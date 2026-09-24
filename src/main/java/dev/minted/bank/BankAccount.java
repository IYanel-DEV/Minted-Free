package dev.minted.bank;

import java.util.UUID;

/**
 * A single player's balance held in memory.
 *
 * <p>Deposits and withdrawals are synchronized so a balance change is always
 * atomic: the guard against the max-balance cap and the mutation happen under
 * the same lock, which is what lets {@link EconomyService#transfer} treat a
 * pair of these as one operation.
 */
public final class BankAccount {

    private final UUID uuid;
    private final double maxBalance;
    private final BalanceChangeSink sink;

    private double balance;
    private volatile boolean dirty;

    /**
     * The balance this account is known to hold in storage. The multi-server
     * saver writes the difference between this baseline and the live balance as
     * an atomic delta, so two servers can never overwrite each other's work and
     * a stale cache cannot smuggle an old value back into the database.
     */
    private double persisted;

    /** Set by admin-style absolute writes, which bypass the delta path. */
    private boolean absolute;

    BankAccount(UUID uuid, double balance, double maxBalance) {
        this(uuid, balance, maxBalance, null);
    }

    BankAccount(UUID uuid, double balance, double maxBalance, BalanceChangeSink sink) {
        this.uuid = uuid;
        this.balance = balance;
        this.persisted = balance;
        this.maxBalance = maxBalance;
        this.sink = sink;
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized double getBalance() {
        return balance;
    }

    /**
     * @return true if the amount was added; false if it would breach the cap
     */
    public boolean deposit(double amount) {
        double before;
        synchronized (this) {
            if (amount <= 0 || balance + amount > maxBalance) {
                return false;
            }
            before = balance;
            balance += amount;
            dirty = true;
        }
        notifyChange(before);
        return true;
    }

    /**
     * @return true if the amount was removed; false on insufficient funds
     */
    public boolean withdraw(double amount) {
        double before;
        synchronized (this) {
            if (amount <= 0 || amount > balance) {
                return false;
            }
            before = balance;
            balance -= amount;
            dirty = true;
        }
        notifyChange(before);
        return true;
    }

    /**
     * Overwrites the balance outright, for administrative writes from a
     * third-party plugin. Rejected if out of range.
     *
     * @return true if the balance was set
     */
    public boolean setBalance(double amount) {
        double before;
        synchronized (this) {
            if (amount < 0 || amount > maxBalance) {
                return false;
            }
            before = balance;
            balance = amount;
            dirty = true;
            // An admin write states the final balance outright, so the saver
            // must write it as-is rather than as a delta.
            absolute = true;
        }
        notifyChange(before);
        return true;
    }

    private void notifyChange(double before) {
        BalanceChangeSink target = sink;
        if (target != null) {
            double after = getBalance();
            if (before != after) {
                target.changed(uuid, before, after);
            }
        }
    }

    boolean isDirty() {
        return dirty;
    }

    /** The balance this account is known to have in storage. */
    double getPersisted() {
        return persisted;
    }

    /** True when the next save must write the balance outright. */
    boolean isAbsolute() {
        return absolute;
    }

    /**
     * Called after a successful save with the balance storage now holds: the
     * live value and the baseline collapse onto the same number, so the next
     * change is measured from the truth.
     */
    synchronized void synced(double stored) {
        this.balance = stored;
        this.persisted = stored;
        this.dirty = false;
        this.absolute = false;
    }

    /**
     * Adopts a balance read back from the shared database - another server
     * moved it, or a write was refused. Silently does nothing while the
     * account has unsaved local changes; those are settled by the saver's own
     * read-back.
     */
    synchronized void remoteRefresh(double stored) {
        if (dirty) {
            return;
        }
        this.balance = stored;
        this.persisted = stored;
    }

    void markClean() {
        dirty = false;
    }

    void markDirty() {
        dirty = true;
    }
}
