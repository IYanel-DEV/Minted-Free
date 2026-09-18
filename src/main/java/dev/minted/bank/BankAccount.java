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

    BankAccount(UUID uuid, double balance, double maxBalance) {
        this(uuid, balance, maxBalance, null);
    }

    BankAccount(UUID uuid, double balance, double maxBalance, BalanceChangeSink sink) {
        this.uuid = uuid;
        this.balance = balance;
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

    void markClean() {
        dirty = false;
    }

    void markDirty() {
        dirty = true;
    }
}
