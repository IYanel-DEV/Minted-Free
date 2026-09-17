package dev.minted.bank;

import dev.minted.backend.LoansDao;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank loans. A loan is money the bank prints: the principal is credited to the
 * player's digital bank account, and what must be repaid is the principal plus
 * a one-time fee. The outstanding balance lives in memory (seeded from storage
 * at startup) and is mirrored to the database off the main thread. Overdue
 * loans accrue a late fee whenever the interest task sweeps.
 */
public final class LoanService {

    private final Plugin plugin;
    private final LoansDao dao;
    private final EconomyService bank;
    private final double max;
    private final double feePercent;
    private final long termMs;
    private final double lateFeePercent;

    private final Map<UUID, Loan> active = new HashMap<UUID, Loan>();
    private int nextId = 1;

    public LoanService(Plugin plugin, LoansDao dao, EconomyService bank,
                       double max, double feePercent, long termMs, double lateFeePercent) {
        this.plugin = plugin;
        this.dao = dao;
        this.bank = bank;
        this.max = max;
        this.feePercent = feePercent;
        this.termMs = termMs;
        this.lateFeePercent = lateFeePercent;
    }

    /** Maximum principal any player may borrow at once. */
    public double maxLoan() {
        return max;
    }

    /** How long a loan has before it is overdue, in milliseconds. */
    public long termMs() {
        return termMs;
    }

    /** The one-time fee charged on a loan, as a percentage of the principal. */
    public double contractRate() {
        return feePercent;
    }

    /** Seeded from storage (blocking); run on an async thread once at startup. */
    public void initialize() {
        dao.createTable();
        nextId = dao.maxId() + 1;
        List<Loan> open = dao.openLoans();
        synchronized (active) {
            active.clear();
            for (Loan loan : open) {
                active.put(loan.borrower(), loan);
                if (loan.id() >= nextId) {
                    nextId = loan.id() + 1;
                }
            }
        }
    }

    /** The player's open loan, or null. Read from the in-memory map. */
    public Loan activeLoan(UUID uuid) {
        synchronized (active) {
            return active.get(uuid);
        }
    }

    /**
     * @return true when the loan was granted and the principal deposited into
     *         the player's bank account
     */
    public boolean take(UUID uuid, double amount) {
        if (amount <= 0 || amount > max) {
            return false;
        }
        BankAccount account = bank.getCached(uuid);
        if (account == null) {
            return false;
        }
        synchronized (active) {
            if (active.containsKey(uuid)) {
                return false;
            }
            if (!account.deposit(amount)) {
                return false;
            }
            double owed = round(amount * (1 + feePercent / 100.0));
            long now = System.currentTimeMillis();
            final int id = nextId++;
            final Loan loan = new Loan(id, uuid, amount, owed, now, now + termMs);
            active.put(uuid, loan);
            persistInsert(loan);
            return true;
        }
    }

    /** The player must have at least the full owed amount in their bank. */
    public boolean repay(UUID uuid) {
        synchronized (active) {
            final Loan loan = active.get(uuid);
            if (loan == null) {
                return false;
            }
            BankAccount account = bank.getCached(uuid);
            if (account == null || !account.withdraw(loan.owed())) {
                return false;
            }
            active.remove(uuid);
            final int id = loan.id();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    dao.repay(id, System.currentTimeMillis());
                }
            });
            return true;
        }
    }

    /**
     * Adds the late fee to every overdue loan. Called on the interest interval;
     * the fee compounds each pass, so an ignored loan keeps growing.
     */
    public void sweepLateFees() {
        List<Loan> snapshot;
        synchronized (active) {
            snapshot = new java.util.ArrayList<Loan>(active.values());
        }
        long now = System.currentTimeMillis();
        for (Loan loan : snapshot) {
            if (loan.dueAt() >= now) {
                continue;
            }
            double next = round(loan.owed() * (1 + lateFeePercent / 100.0));
            synchronized (active) {
                if (active.get(loan.borrower()) != loan || loan.owed() == next) {
                    continue;
                }
                active.put(loan.borrower(), new Loan(loan.id(), loan.borrower(), loan.amount(), next,
                        loan.takenAt(), loan.dueAt()));
                final int id = loan.id();
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        dao.setOwed(id, next);
                    }
                });
            }
        }
    }

    private void persistInsert(final Loan loan) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.insert(loan.id(), loan.borrower(), loan.amount(), loan.owed(),
                            loan.takenAt(), loan.dueAt());
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Could not persist a loan: " + e.getMessage());
                }
            }
        });
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}