package dev.minted.shop;

import org.bukkit.inventory.ItemStack;

/**
 * One tradeable entry in a shop, pinned to a page and a slot in that page's
 * grid. A price of {@link #NOT_OFFERED} means that side of the trade is closed:
 * an item can be buy-only, sell-only, or both. The category is a free-form tag
 * used to group items on the shop's home page; {@code null} means uncategorised.
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
