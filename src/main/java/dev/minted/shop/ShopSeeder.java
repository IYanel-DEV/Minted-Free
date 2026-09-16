package dev.minted.shop;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.shop.catalog.Catalog;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Seeds the two shops a fresh install needs: the admin {@code Spawn} global
 * shop, stocked from the version-aware {@link Catalog} (so a 1.8 server never
 * gets elytra), and the single {@code Community} marketplace. The community
 * shop is (re)created whenever it is missing, which is how a database upgraded
 * from before v0.10.0 gains one without a wipe.
 */
final class ShopSeeder {

    private ShopSeeder() {
    }

    static void seed(ShopService shops, ServerVersion version, MaterialLookup materials, boolean fresh) {
        if (fresh) {
            seedGlobal(shops, version, materials);
        }
        if (shops.community() == null) {
            seedCommunity(shops, materials);
        }
    }

    private static void seedGlobal(ShopService shops, ServerVersion version, MaterialLookup materials) {
        Shop shop = shops.create("Spawn", named(Material.EMERALD, ChatColor.GREEN + "Spawn Shop"), Currency.WALLET);
        int slot = 0;
        for (Catalog.Entry entry : Catalog.entriesFor(version)) {
            Material material = materials.get(entry.materialKey);
            if (material == null) {
                continue;
            }
            ShopItem item = new ShopItem(shop.getId(), slot / Shop.SLOTS_PER_PAGE, slot % Shop.SLOTS_PER_PAGE,
                    named(material, ChatColor.WHITE + entry.display), entry.buy, entry.sell, entry.category.key());
            shops.saveItem(shop, item);
            slot++;
        }
    }

    private static void seedCommunity(ShopService shops, MaterialLookup materials) {
        Material chest = materials.get("chest");
        ItemStack icon = named(chest == null ? Material.CHEST : chest, ChatColor.YELLOW + "Community Market");
        shops.create("Community", icon, Currency.WALLET, ShopType.COMMUNITY);
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
