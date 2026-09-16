package dev.minted.shop.storage;

/**
 * A raw {@code shop_items} row. The item stack is still Base64 text; the service
 * decodes it (and skips the row if it cannot) when assembling a shop.
 */
public final class ShopItemRow {

    private final int shopId;
    private final int page;
    private final int slot;
    private final String itemData;
    private final double buyPrice;
    private final double sellPrice;
    private final String category;
    private final String owner;
    private final long stock;
    private final double buyBack;
    private final double earnings;

    public ShopItemRow(int shopId, int page, int slot, String itemData,
                       double buyPrice, double sellPrice, String category,
                       String owner, long stock, double buyBack, double earnings) {
        this.shopId = shopId;
        this.page = page;
        this.slot = slot;
        this.itemData = itemData;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.category = category;
        this.owner = owner;
        this.stock = stock;
        this.buyBack = buyBack;
        this.earnings = earnings;
    }

    public String getOwner() {
        return owner;
    }

    public long getStock() {
        return stock;
    }

    public double getBuyBack() {
        return buyBack;
    }

    public double getEarnings() {
        return earnings;
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

    public String getItemData() {
        return itemData;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public String getCategory() {
        return category;
    }
}
