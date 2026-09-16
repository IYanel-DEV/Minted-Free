package dev.minted.shop.storage;

/**
 * A raw {@code shops} row as read from the database. The icon is still Base64
 * text here; the service decodes it on the main thread when building the model.
 */
public final class ShopRow {

    private final int id;
    private final String name;
    private final String iconData;
    private final String currency;
    private final String type;

    public ShopRow(int id, String name, String iconData, String currency, String type) {
        this.id = id;
        this.name = name;
        this.iconData = iconData;
        this.currency = currency;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconData() {
        return iconData;
    }

    public String getCurrency() {
        return currency;
    }

    public String getType() {
        return type;
    }
}
