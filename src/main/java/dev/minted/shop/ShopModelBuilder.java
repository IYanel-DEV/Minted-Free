package dev.minted.shop;

import dev.minted.shop.storage.ShopItemRow;
import dev.minted.shop.storage.ShopRow;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Turns raw database rows into the in-memory shop model. A row whose icon or
 * item cannot be deserialised is skipped with a single log line rather than
 * failing the whole load, so one bad entry never takes a shop - or every shop -
 * down. Runs on the main thread, where item deserialisation is safe.
 */
final class ShopModelBuilder {

    private final Logger log;

    ShopModelBuilder(Logger log) {
        this.log = log;
    }

    Result build(List<ShopRow> shopRows, List<ShopItemRow> itemRows) {
        Map<String, Shop> shops = new LinkedHashMap<String, Shop>();
        int nextId = 1;
        for (ShopRow row : shopRows) {
            Shop shop = toShop(row);
            if (shop != null) {
                shops.put(row.getName().toLowerCase(Locale.ROOT), shop);
                nextId = Math.max(nextId, shop.getId() + 1);
            }
        }
        for (ShopItemRow row : itemRows) {
            attachItem(shops.values(), row);
        }
        return new Result(shops, nextId);
    }

    private Shop toShop(ShopRow row) {
        try {
            ItemStack icon = ItemCodec.decode(row.getIconData());
            return new Shop(row.getId(), row.getName(), icon, Currency.fromId(row.getCurrency()),
                    ShopType.fromId(row.getType()));
        } catch (ItemCodec.DecodeException e) {
            log.warning("Skipping shop '" + row.getName() + "': its icon could not be read.");
            return null;
        }
    }

    private void attachItem(Collection<Shop> shops, ShopItemRow row) {
        Shop shop = byId(shops, row.getShopId());
        if (shop == null) {
            return;
        }
        try {
            ItemStack item = ItemCodec.decode(row.getItemData());
            ShopItem shopItem = new ShopItem(shop.getId(), row.getPage(), row.getSlot(), item,
                    row.getBuyPrice(), row.getSellPrice(), row.getCategory());
            shopItem.setStock(row.getStock());
            shopItem.setBuyBackPrice(row.getBuyBack());
            shopItem.setOwner(parseUuid(row.getOwner()));
            shopItem.setEarnings(row.getEarnings());
            shop.put(shopItem);
        } catch (ItemCodec.DecodeException e) {
            log.warning("Skipping an item in shop '" + shop.getName() + "' at page "
                    + row.getPage() + " slot " + row.getSlot() + ": it could not be read.");
        }
    }

    private static java.util.UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Shop byId(Collection<Shop> shops, int id) {
        for (Shop shop : shops) {
            if (shop.getId() == id) {
                return shop;
            }
        }
        return null;
    }

    /** The assembled shops keyed by lower-cased name, plus the next free id. */
    static final class Result {
        final Map<String, Shop> shops;
        final int nextId;

        Result(Map<String, Shop> shops, int nextId) {
            this.shops = shops;
            this.nextId = nextId;
        }
    }
}
