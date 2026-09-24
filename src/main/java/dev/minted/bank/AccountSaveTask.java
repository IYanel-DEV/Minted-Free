package dev.minted.bank;

import dev.minted.backend.StorageProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic batch save of changed balances. Runs on an async scheduler thread,
 * so it may call storage directly.
 *
 * <p>An account is marked clean before its snapshot is written. If a balance
 * changes again mid-save the flag flips back to dirty and the value is caught
 * next tick; if the save fails, the affected accounts are re-flagged so nothing
 * is silently lost.
 */
public final class AccountSaveTask implements Runnable {

    private final EconomyService economy;
    private final StorageProvider storage;

    public AccountSaveTask(EconomyService economy, StorageProvider storage) {
        this.economy = economy;
        this.storage = storage;
    }

    @Override
    public void run() {
        if (economy.isNetworked()) {
            // A shared database must never be fed absolute overwrites: the
            // networked saver commits guarded deltas instead.
            economy.flushDirty();
            return;
        }
        Map<java.util.UUID, Double> snapshot = new HashMap<java.util.UUID, Double>();
        List<BankAccount> flushed = new ArrayList<BankAccount>();

        for (BankAccount account : economy.cache().values()) {
            if (account.isDirty()) {
                account.markClean();
                snapshot.put(account.getUuid(), account.getBalance());
                flushed.add(account);
            }
        }

        if (snapshot.isEmpty()) {
            return;
        }

        try {
            storage.saveBalances(snapshot);
        } catch (RuntimeException e) {
            for (BankAccount account : flushed) {
                account.markDirty();
            }
            throw e;
        }
    }
}
