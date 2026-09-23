package dev.minted.auction.storage;

import java.util.UUID;

/**
 * One bid row from the database.
 */
public final class AuctionBidRow {

    private final UUID bidder;
    private final double amount;
    private final long timestamp;

    public AuctionBidRow(UUID bidder, double amount, long timestamp) {
        this.bidder = bidder;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public UUID getBidder() {
        return bidder;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}