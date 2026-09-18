package dev.minted.bounty;

import java.util.UUID;

/**
 * A single posted bounty: money held {"escrowed"} by leaving it out of the
 * placer's bank, earned by whoever kills the target.
 */
public final class Bounty {

    public static final int OPEN = 0;
    public static final int CLAIMED = 1;
    public static final int REFUNDED = 2;

    private final int id;
    private final UUID target;
    private final UUID placer;
    private final double amount;
    private final String note;
    private final long placedAt;

    public Bounty(int id, UUID target, UUID placer, double amount, String note, long placedAt) {
        this.id = id;
        this.target = target;
        this.placer = placer;
        this.amount = amount;
        this.note = note;
        this.placedAt = placedAt;
    }

    public int id() {
        return id;
    }

    public UUID target() {
        return target;
    }

    public UUID placer() {
        return placer;
    }

    public double amount() {
        return amount;
    }

    /** The reason shown on the board, or null when the placer left no note. */
    public String note() {
        return note == null || note.isEmpty() ? null : note;
    }

    public long placedAt() {
        return placedAt;
    }
}