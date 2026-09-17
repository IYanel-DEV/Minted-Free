package dev.minted.bank;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bank's payout: each account with a positive stored balance earns a small
 * percentage every interval, and any overdue loans accrue their late fee in the
 * same pass. Interest is added to the stored values and to the live cached
 * accounts; the economy stats pick it up on their own refresh, since the bank
 * total is read from these same balances. Runs on a repeating async task.
 */
public final class InterestTask implements Runnable {

    private final Plugin plugin;
    private final EconomyService bank;
    private final double ratePercent;

    /** May be null when loans are disabled; the sweep is skipped then. */
    private final LoanService loans;

    public InterestTask(Plugin plugin, EconomyService bank, double ratePercent, LoanService loans) {
        this.plugin = plugin;
        this.bank = bank;
        this.ratePercent = ratePercent;
        this.loans = loans;
    }

    @Override
    public void run() {
        try {
            payInterest();
            if (loans != null) {
                loans.sweepLateFees();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Interest payout failed: " + e.getMessage());
        }
    }

    private void payInterest() {
        Map<UUID, Double> stored = bank.allBalances();
        if (stored.isEmpty()) {
            return;
        }
        double max = bank.maxBalance();
        // What each stored account earns, capped so it never passes the limit.
        Map<UUID, Double> interest = new HashMap<UUID, Double>();
        Map<UUID, Double> nextStored = new HashMap<UUID, Double>();
        for (Map.Entry<UUID, Double> entry : stored.entrySet()) {
            double balance = entry.getValue();
            if (balance <= 0) {
                continue;
            }
            double raw = balance * ratePercent / 100.0;
            double earned = Math.min(round(raw), max - balance);
            if (earned <= 0) {
                continue;
            }
            interest.put(entry.getKey(), earned);
            nextStored.put(entry.getKey(), balance + earned);
        }
        if (nextStored.isEmpty()) {
            return;
        }
        // Offline accounts: persist the new stored balances directly.
        Map<UUID, Double> persisted = new HashMap<UUID, Double>();
        for (Map.Entry<UUID, Double> entry : nextStored.entrySet()) {
            if (bank.getCached(entry.getKey()) == null) {
                persisted.put(entry.getKey(), entry.getValue());
            }
        }
        if (!persisted.isEmpty()) {
            bank.persistBalances(persisted);
        }
        // Online accounts: deposit the earned amount into the live cache; the
        // regular batch saver persists them (the stored row and the cache start
        // from the same saved value, so each earns exactly once).
        for (Map.Entry<UUID, Double> entry : interest.entrySet()) {
            BankAccount account = bank.getCached(entry.getKey());
            if (account != null) {
                account.deposit(entry.getValue());
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}