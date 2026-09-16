package dev.minted.shop;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One tradeable entry in a shop, pinned to a page and a slot in that page's
 * grid. A price of {@link #NOT_OFFERED} means that side of the trade is closed:
 * an item can be buy-only, sell-only, or both. The category groups items on the
 * home screen; {@code null} reads as {@code Misc}.
 *
 * <p>The last four fields matter only for community-marketplace listings: {@link
 * #stock} is the real unit count a player deposited, {@link #buyBackPrice} the
 * price the owner pays to buy units back ({@link #NOT_OFFERED} = closed), {@link
 * #owner} the seller, and {@link #earnings} their uncollected takings. Global
 * shop items leave them at their defaults and ignore them.
 */
public final class ShopItem {

    /** Sentinel price meaning "this side of the trade is not offered". */
    public static final double NOT_OFFERED = -1.0D;

    private final int shopId;
    private final int page;
    private final int slot;

    private ItemStack item;
    private double buyPrice;
    private double sellPrice;
    private String category;

    private long stock;
    private double buyBackPrice = NOT_OFFERED;
    private UUID owner;
    private double earnings;

    public ShopItem(int shopId, int page, int slot, ItemStack item,
                    double buyPrice, double sellPrice, String category) {
        this.shopId = shopId;
        this.page = page;
        this.slot = slot;
        this.item = item;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.category = category;
    }

    public long getStock() {
        return stock;
    }

    public void setStock(long stock) {
        this.stock = Math.max(0L, stock);
    }

    public double getBuyBackPrice() {
        return buyBackPrice;
    }

    public void setBuyBackPrice(double buyBackPrice) {
        this.buyBackPrice = buyBackPrice;
    }

    public boolean buysBack() {
        return buyBackPrice >= 0;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public double getEarnings() {
        return earnings;
    }

    public void setEarnings(double earnings) {
        this.earnings = Math.max(0.0D, earnings);
    }

    public void addEarnings(double amount) {
        setEarnings(this.earnings + amount);
    }

    public int getShopId() {
        return shopId;
    }

    public int getPage() {
        return page;
    }

    public int getSlot() {
        return slot;
    }

    /** A defensive copy of the stored stack, safe to mutate for display or giving. */
    public ItemStack copy() {
        return item.clone();
    }

    ItemStack raw() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = buyPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = sellPrice;
    }

    public boolean isBuyable() {
        return buyPrice >= 0;
    }

    public boolean isSellable() {
        return sellPrice >= 0;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
