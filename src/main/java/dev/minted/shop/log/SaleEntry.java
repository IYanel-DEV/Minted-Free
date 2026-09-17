package dev.minted.shop.log;

import java.util.UUID;

/**
 * One recorded sale shown in the server-wide sales feed. The buyer or seller
 * side is empty when the shop was the counterparty (a player bought from or
 * sold to a shop, not another player).
 */
public final class SaleEntry {

    private final long ts;
    private final String seller;
    private final String buyer;
    private final String item;
    private final int qty;
    private final double price;
    private final String kind;

    public SaleEntry(long ts, String seller, String buyer, String item, int qty, double price, String kind) {
        this.ts = ts;
        this.seller = seller;
        this.buyer = buyer;
        this.item = item;
        this.qty = qty;
        this.price = price;
        this.kind = kind;
    }

    /** Epoch millis when the sale happened. */
    public long ts() {
        return ts;
    }

    /** The seller's uuid, or "" for the shop. */
    public String seller() {
        return seller;
    }

    /** The buyer's uuid, or "" for the shop. */
    public String buyer() {
        return buyer;
    }

    /** Cleaned display name of the goods, or the shop name context. */
    public String item() {
        return item;
    }

    public int qty() {
        return qty;
    }

    public double price() {
        return price;
    }

    /** "shop" or "market". */
    public String kind() {
        return kind;
    }

    public UUID sellerUuid() {
        return uuid(seller);
    }

    public UUID buyerUuid() {
        return uuid(buyer);
    }

    public boolean isShopSale() {
        return "shop".equals(kind);
    }

    private static UUID uuid(String raw) {
        return (raw == null || raw.isEmpty()) ? null : UUID.fromString(raw);
    }
}