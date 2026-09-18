package dev.minted.bounty;

import dev.minted.backend.BountyDao;
import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player bounties. Posting a bounty draws the reward out of the placer's bank
 * and keeps it parked on the posting itself - the money is real, it just is
 * not spendable while the bounty is open. A player kill pays every open bounty
 * on the target into the killer's bank; the placer (or an admin) can pull a
 * bounty back for a full refund anytime. The open set lives in memory, seeded
 * from storage at startup and mirrored off the main thread, like loans.
 */
public final class BountyService {

    /** All money-safety checks and their user-facing outcome. */
    public enum Result {
        OK,
        NOT_READY,
        SELF,
        INVALID,
        MIN,
        MAX,
        SHORT,
        KILLER_SHORT
    }

    private final Plugin plugin;
    private final BountyDao dao;
    private final EconomyService bank;
    private final double minAmount;
    private final double maxAmount;

    private final Map<Integer, Bounty> open = new LinkedHashMap<Integer, Bounty>();
    private int nextId = 1;

    public BountyService(Plugin plugin, BountyDao dao, EconomyService bank, double minAmount, double maxAmount) {
        this.plugin = plugin;
        this.dao = dao;
        this.bank = bank;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    /** Seeded from storage (blocking); run on an async thread once at startup. */
    public void initialize() {
        dao.createTable();
        int highest = dao.maxId();
        nextId = highest + 1;
        synchronized (open) {
            open.clear();
            for (Bounty bounty : dao.openBounties()) {
                open.put(bounty.id(), bounty);
                if (bounty.id() >= nextId) {
                    nextId = bounty.id() + 1;
                }
            }
        }
    }

    public double minAmount() {
        return minAmount;
    }

    public double maxAmount() {
        return maxAmount;
    }

    /** Snapshot of every open bounty, in posting order. Main thread friendly. */
    public List<Bounty> listed() {
        synchronized (open) {
            return new ArrayList<Bounty>(open.values());
        }
    }

    /** Every open bounty on a target, or an empty list. */
    public List<Bounty> pendingOn(UUID target) {
        List<Bounty> matches = new ArrayList<Bounty>();
        synchronized (open) {
            for (Bounty bounty : open.values()) {
                if (bounty.target().equals(target)) {
                    matches.add(bounty);
                }
            }
        }
        return matches;
    }

    public Bounty byId(int id) {
        synchronized (open) {
            return open.get(id);
        }
    }

    /**
     * Posts a bounty (or stacks another reward on top of it). Withdraws the
     * reward from the placer's bank before the posting is registered. Returns
     * {@link Result#OK} when the bounty is live.
     */
    public Result place(UUID target, UUID placer, double amount, String note) {
        if (target == null || placer == null || amount <= 0) {
            return Result.INVALID;
        }
        if (target.equals(placer)) {
            return Result.SELF;
        }
        if (amount < minAmount) {
            return Result.MIN;
        }
        if (amount > maxAmount) {
            return Result.MAX;
        }
        double currentTotal = 0;
        synchronized (open) {
            for (Bounty b : open.values()) {
                if (b.target().equals(target)) currentTotal += b.amount();
            }
        }
        if (currentTotal + amount > maxAmount) {
            return Result.MAX;
        }
        BankAccount account = bank.getCached(placer);
        if (account == null) {
            return Result.NOT_READY;
        }
        synchronized (account) {
            if (!account.withdraw(amount)) {
                return Result.SHORT;
            }
            long now = System.currentTimeMillis();
            final int id = nextId++;
            final Bounty bounty = new Bounty(id, target, placer, amount,
                    note == null ? null : note.trim(), now);
            synchronized (open) {
                open.put(id, bounty);
            }
            persistInsert(bounty, placer);
        }
        return Result.OK;
    }

    /**
     * Pays out every open bounty on the target to the killer and closes them.
     * Runs on the main thread; writes happen off it. Returns the total paid,
     * or 0 when there was nothing to collect or the killer could not be paid.
     */
    public double claimByKill(UUID target, UUID killer) {
        if (target == null || killer == null) {
            return 0;
        }
        final List<Bounty> pending = pendingOn(target);
        if (pending.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Bounty bounty : pending) {
            total += bounty.amount();
        }
        BankAccount account = bank.getCached(killer);
        if (account == null) {
            return 0;
        }
        synchronized (account) {
            if (!account.deposit(total)) {
                return -1;
            }
        }
        final long now = System.currentTimeMillis();
        final double paid = total;
        synchronized (open) {
            for (Bounty bounty : pending) {
                open.remove(bounty.id());
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dao.markClaimed(bounty.id(), killer, now);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("Could not mark a bounty claimed: " + e.getMessage());
                        }
                    }
                });
            }
        }
        return paid;
    }

    /** Pays a bounty back into the placer's bank and closes it. */
    public boolean refund(Bounty bounty, UUID actor, boolean admin) {
        if (bounty == null) {
            return false;
        }
        boolean owned = admin || bounty.placer().equals(actor);
        if (!owned) {
            return false;
        }
        BankAccount account = bank.getCached(bounty.placer());
        if (account == null) {
            return false;
        }
        synchronized (account) {
            if (!account.deposit(bounty.amount())) {
                return false;
            }
        }
        final int id = bounty.id();
        synchronized (open) {
            if (open.remove(id) == null) {
                return false;
            }
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.markRefunded(id);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Could not mark a bounty refunded: " + e.getMessage());
                }
            }
        });
        return true;
    }

    // A failed write would strand the placer's money in a row that a restart
    // forgets, so the withdrawal is rolled straight back into their bank.
    private void persistInsert(final Bounty bounty, final UUID placer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.insert(bounty.id(), bounty.target(), bounty.placer(), bounty.amount(),
                            bounty.note(), bounty.placedAt());
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Could not persist a bounty: " + e.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            BankAccount account = bank.getCached(placer);
                            if (account != null) {
                                account.deposit(bounty.amount());
                            }
                            synchronized (open) {
                                open.remove(bounty.id());
                            }
                            plugin.getLogger().warning("Refunded a bounty whose posting was not saved.");
                        }
                    });
                }
            }
        });
    }
}