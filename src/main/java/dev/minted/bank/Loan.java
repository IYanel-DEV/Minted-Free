package dev.minted.bank;

import java.util.UUID;

/** A single bank loan: the principal taken, what must be repaid, and its deadline. */
public final class Loan {

    private final int id;
    private final UUID borrower;
    private final double amount;
    private final double owed;
    private final long takenAt;
    private final long dueAt;

    public Loan(int id, UUID borrower, double amount, double owed, long takenAt, long dueAt) {
        this.id = id;
        this.borrower = borrower;
        this.amount = amount;
        this.owed = owed;
        this.takenAt = takenAt;
        this.dueAt = dueAt;
    }

    public int id() {
        return id;
    }

    public UUID borrower() {
        return borrower;
    }

    /** What the bank lent (before any fees). */
    public double amount() {
        return amount;
    }

    /** What must be repaid today: principal plus fees and any late fees. */
    public double owed() {
        return owed;
    }

    public long takenAt() {
        return takenAt;
    }

    public long dueAt() {
        return dueAt;
    }

    /** Milliseconds until the deadline; negative once overdue. */
    public long timeLeft() {
        return dueAt - System.currentTimeMillis();
    }
}