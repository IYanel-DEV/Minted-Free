package dev.minted.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One auction listing. A seller places an item with a starting bid and an
 * optional buyout price. Bidders place bids; the highest bid wins when the
 * auction expires, unless someone hits the buyout.
 */
public final class AuctionItem {

    private final long id;
    private final UUID seller;
    private final ItemStack item;
    private final double startPrice;
    private final double buyoutPrice;
    private final long endsAt;
    private final long createdAt;

    private double currentBid;
    private UUID highestBidder;
    private int bidCount;
    private AuctionState state;

    public AuctionItem(long id, UUID seller, ItemStack item, double startPrice,
                       double buyoutPrice, long endsAt, long createdAt) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.startPrice = startPrice;
        this.buyoutPrice = buyoutPrice;
        this.endsAt = endsAt;
        this.createdAt = createdAt;
        this.currentBid = 0;
        this.highestBidder = null;
        this.bidCount = 0;
        this.state = AuctionState.ACTIVE;
    }

    public long getId() {
        return id;
    }

    public UUID getSeller() {
        return seller;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public ItemStack getRawItem() {
        return item;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public double getBuyoutPrice() {
        return buyoutPrice;
    }

    public boolean hasBuyout() {
        return buyoutPrice > 0;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public UUID getHighestBidder() {
        return highestBidder;
    }

    public void setHighestBidder(UUID highestBidder) {
        this.highestBidder = highestBidder;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void incrementBidCount() {
        this.bidCount++;
    }

    public AuctionState getState() {
        return state;
    }

    public void setState(AuctionState state) {
        this.state = state;
    }

    public boolean isActive() {
        return state == AuctionState.ACTIVE && System.currentTimeMillis() < endsAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endsAt;
    }

    public long getTimeRemaining() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    public double getEffectivePrice() {
        return currentBid > 0 ? currentBid : startPrice;
    }
}