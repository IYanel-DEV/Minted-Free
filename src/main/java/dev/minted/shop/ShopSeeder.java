package dev.minted.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Seeds a single starter shop on a fresh install so {@code /eshop} is not empty
 * on first run. Every material named here has kept the same enum name from 1.8
 * to 1.26, the same guarantee the menu icons rely on, so the shop renders on any
 * supported server. Richer per-version stock is out of scope.
 */
final class ShopSeeder {

    private ShopSeeder() {
    }

    static void seed(ShopService shops) {
        Shop shop = shops.create("Spawn", named(Material.EMERALD, ChatColor.GREEN + "Spawn Shop"), Currency.WALLET);
        int slot = 0;
        for (Seed seed : STOCK) {
            ShopItem item = new ShopItem(shop.getId(), 0, slot, new ItemStack(seed.material),
                    seed.buy, seed.sell, seed.category);
            shops.saveItem(shop, item);
            slot++;
        }
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static final class Seed {
        private final Material material;
        private final double buy;
        private final double sell;
        private final String category;

        private Seed(Material material, double buy, double sell, String category) {
            this.material = material;
            this.buy = buy;
            this.sell = sell;
            this.category = category;
        }
    }

    private static final Seed[] STOCK = {
            new Seed(Material.DIRT, 5, 1, "Blocks"),
            new Seed(Material.STONE, 10, 2, "Blocks"),
            new Seed(Material.COBBLESTONE, 8, 2, "Blocks"),
            new Seed(Material.GLASS, 12, 3, "Blocks"),
            new Seed(Material.BREAD, 15, 4, "Food"),
            new Seed(Material.APPLE, 20, 5, "Food"),
            new Seed(Material.IRON_INGOT, 100, 40, "Ores"),
            new Seed(Material.GOLD_INGOT, 200, 80, "Ores"),
            new Seed(Material.DIAMOND, 500, 200, "Ores"),
    };
}
