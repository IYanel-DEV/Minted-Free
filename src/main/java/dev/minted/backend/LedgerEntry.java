package dev.minted.backend;

/**
 * One recorded money movement in a player's personal history: when it happened,
 * the signed delta to that player's balance, what kind of event it was, and a
 * short detail (the other party, the item, the reason).
 */
public final class LedgerEntry {

    private final long ts;
    private final double delta;
    private final String kind;
    private final String detail;

    public LedgerEntry(long ts, double delta, String kind, String detail) {
        this.ts = ts;
        this.delta = delta;
        this.kind = kind;
        this.detail = detail;
    }

    /** Epoch millis when the movement happened. */
    public long ts() {
        return ts;
    }

    /** Amount added to the player's balance; negative for money leaving it. */
    public double delta() {
        return delta;
    }

    /** Stable event name, e.g. {@code pay}, {@code deposit}, {@code shop-buy}. */
    public String kind() {
        return kind;
    }

    /** Human detail: the other player, the item, or the reason. May be empty. */
    public String detail() {
        return detail == null ? "" : detail;
    }
}