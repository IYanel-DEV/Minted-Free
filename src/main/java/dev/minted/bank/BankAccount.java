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

    private double balance;
    private volatile boolean dirty;

    BankAccount(UUID uuid, double balance, double maxBalance) {
        this.uuid = uuid;
        this.balance = balance;
        this.maxBalance = maxBalance;
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
    public synchronized boolean deposit(double amount) {
        if (amount <= 0 || balance + amount > maxBalance) {
            return false;
        }
        balance += amount;
        dirty = true;
        return true;
    }

    /**
     * @return true if the amount was removed; false on insufficient funds
     */
    public synchronized boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        dirty = true;
        return true;
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
