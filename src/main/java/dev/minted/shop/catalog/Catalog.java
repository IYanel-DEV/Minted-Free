package dev.minted.shop.catalog;

import dev.minted.compat.ServerVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * The code-defined preset stock the global shop is seeded with. Each entry names
 * a version-safe material key (resolved through {@code MaterialLookup}), a
 * {@link Category}, buy/sell prices, and the earliest Minecraft minor version
 * ({@code since}, on the 1.x line) whose material exists. {@link #entriesFor}
 * drops anything newer than the running server, so a 1.8 seed never lists elytra
 * or shulker boxes and a 1.13+ one does.
 */
public final class Catalog {

    public static final class Entry {
        public final String display;
        public final String materialKey;
        public final Category category;
        public final double buy;
        public final double sell;
        public final int since;

        Entry(String display, String materialKey, Category category, double buy, double sell, int since) {
            this.display = display;
            this.materialKey = materialKey;
            this.category = category;
            this.buy = buy;
            this.sell = sell;
            this.since = since;
        }
    }

    private Catalog() {
    }

    /** The preset entries this server's version can actually show. */
    public static List<Entry> entriesFor(ServerVersion version) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry entry : ALL) {
            if (version.isAtLeast(1, entry.since)) {
                out.add(entry);
            }
        }
        return out;
    }

    private static Entry e(String display, String key, Category cat, double buy, double sell, int since) {
        return new Entry(display, key, cat, buy, sell, since);
    }

    private static final Entry[] ALL = {
            // Building.
            e("Stone", "stone", Category.BUILDING, 10, 2, 8),
            e("Cobblestone", "cobblestone", Category.BUILDING, 8, 2, 8),
            e("Oak Planks", "oak_planks", Category.BUILDING, 12, 3, 8),
            e("Glass", "glass", Category.BUILDING, 14, 3, 8),
            e("Oak Log", "oak_log", Category.BUILDING, 20, 5, 8),
            e("Sandstone", "sandstone", Category.BUILDING, 12, 3, 8),
            e("Bricks", "bricks", Category.BUILDING, 30, 8, 8),

            // Tools & weapons.
            e("Wooden Sword", "wooden_sword", Category.TOOLS, 25, 5, 8),
            e("Stone Axe", "stone_axe", Category.TOOLS, 40, 8, 8),
            e("Iron Pickaxe", "iron_pickaxe", Category.TOOLS, 220, 60, 8),
            e("Bow", "bow", Category.TOOLS, 120, 30, 8),
            e("Fishing Rod", "fishing_rod", Category.TOOLS, 90, 20, 8),
            e("Shield", "shield", Category.TOOLS, 150, 40, 9),

            // Armor & combat.
            e("Leather Chestplate", "leather_chestplate", Category.ARMOR, 60, 15, 8),
            e("Iron Helmet", "iron_helmet", Category.ARMOR, 180, 50, 8),
            e("Diamond Chestplate", "diamond_chestplate", Category.ARMOR, 1600, 500, 8),
            e("Shulker Box", "shulker_box", Category.ARMOR, 3000, 900, 11),
            e("Totem of Undying", "totem", Category.ARMOR, 5000, 1500, 11),
            e("Elytra", "elytra", Category.ARMOR, 8000, 2500, 9),

            // Food.
            e("Apple", "apple", Category.FOOD, 20, 5, 8),
            e("Bread", "bread", Category.FOOD, 15, 4, 8),
            e("Cooked Beef", "cooked_beef", Category.FOOD, 30, 8, 8),
            e("Cooked Chicken", "cooked_chicken", Category.FOOD, 25, 6, 8),
            e("Golden Apple", "golden_apple", Category.FOOD, 400, 120, 8),
            e("Carrot", "carrot", Category.FOOD, 12, 3, 8),

            // Ores & materials.
            e("Coal", "coal", Category.ORES, 20, 5, 8),
            e("Iron Ingot", "iron_ingot", Category.ORES, 100, 40, 8),
            e("Gold Ingot", "gold_ingot", Category.ORES, 200, 80, 8),
            e("Diamond", "diamond", Category.ORES, 500, 200, 8),
            e("Emerald", "emerald", Category.ORES, 600, 240, 8),
            e("Lapis Lazuli", "lapis", Category.ORES, 60, 20, 8),

            // Redstone & mechanics.
            e("Redstone", "redstone", Category.REDSTONE, 15, 4, 8),
            e("Redstone Torch", "redstone_torch", Category.REDSTONE, 20, 5, 8),
            e("Piston", "piston", Category.REDSTONE, 80, 20, 8),
            e("Hopper", "hopper", Category.REDSTONE, 250, 70, 8),
            e("Comparator", "comparator", Category.REDSTONE, 60, 15, 8),
            e("Observer", "observer", Category.REDSTONE, 90, 25, 11),

            // Decoration.
            e("Flower Pot", "flower_pot", Category.DECORATION, 20, 5, 8),
            e("Item Frame", "item_frame", Category.DECORATION, 30, 8, 8),
            e("Torch", "torch", Category.DECORATION, 6, 1, 8),
            e("Glowstone", "glowstone", Category.DECORATION, 40, 10, 8),

            // Transport.
            e("Rail", "rail", Category.TRANSPORT, 25, 6, 8),
            e("Powered Rail", "powered_rail", Category.TRANSPORT, 120, 30, 8),
            e("Boat", "boat", Category.TRANSPORT, 40, 10, 8),
            e("Saddle", "saddle", Category.TRANSPORT, 300, 90, 8),

            // Nature.
            e("Oak Sapling", "oak_sapling", Category.NATURE, 15, 3, 8),
            e("Wheat Seeds", "wheat_seeds", Category.NATURE, 8, 2, 8),
            e("Bone Meal", "bone_meal", Category.NATURE, 12, 3, 8),
            e("Dirt", "dirt", Category.NATURE, 5, 1, 8),

            // Brewing & alchemy.
            e("Glass Bottle", "glass_bottle", Category.BREWING, 15, 3, 8),
            e("Blaze Powder", "blaze_powder", Category.BREWING, 80, 20, 8),
            e("Fermented Spider Eye", "fermented_spider_eye", Category.BREWING, 60, 15, 8),
            e("Nether Wart", "nether_wart", Category.BREWING, 40, 10, 8),

            // Enchanted / special.
            e("Bottle o' Enchanting", "experience_bottle", Category.ENCHANTED, 200, 60, 8),
            e("Ender Pearl", "ender_pearl", Category.ENCHANTED, 150, 40, 8),
            e("Enchanted Book", "enchanted_book", Category.ENCHANTED, 500, 150, 8),
            e("Name Tag", "name_tag", Category.MISC, 100, 25, 8),
            e("Book", "book", Category.MISC, 30, 8, 8),
    };
}
