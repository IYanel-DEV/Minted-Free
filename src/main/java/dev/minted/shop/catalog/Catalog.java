package dev.minted.shop.catalog;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * The code-defined preset stock the global shop is seeded with. Each entry names
 * a version-safe material key (resolved and gated through {@code MaterialLookup}),
 * a {@link Category}, and buy/sell prices. The earliest version an entry can
 * appear on is that of its material key in the registry ({@code MaterialLookup#since}),
 * so the registry is the single source of truth: a 1.8 seed never lists elytra,
 * a 1.16 one adds netherite, a 1.17 one adds copper and amethyst, a 1.19 one adds
 * sculk and mangrove, and so on up to 1.26. {@link #entriesFor} hands back exactly
 * the entries the running server's version can carry.
 */
public final class Catalog {

    public static final class Entry {
        public final String display;
        public final String materialKey;
        public final Category category;
        public final double buy;
        public final double sell;
        public final int since;

        Entry(String display, String materialKey, Category category, double buy, double sell) {
            this.display = display;
            this.materialKey = materialKey;
            this.category = category;
            this.buy = buy;
            this.sell = sell;
            this.since = MaterialLookup.since(materialKey);
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

    /** The curated entry for a canonical material key, or null when not catalogued. */
    public static Entry find(String materialKey) {
        if (materialKey == null) {
            return null;
        }
        for (Entry entry : ALL) {
            if (materialKey.equals(entry.materialKey)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * A sane default pair of {buy, sell} for a category. Used when a material is
     * not curated (the "sell every item" fill), so every item still has a price.
     */
    public static double[] defaultPrice(Category category) {
        switch (category) {
            case TOOLS: return new double[]{60, 15};
            case ARMOR: return new double[]{120, 30};
            case FOOD: return new double[]{15, 4};
            case ORES: return new double[]{40, 12};
            case REDSTONE: return new double[]{50, 14};
            case DECORATION: return new double[]{30, 8};
            case TRANSPORT: return new double[]{200, 60};
            case NATURE: return new double[]{20, 5};
            case BREWING: return new double[]{100, 28};
            case ENCHANTED: return new double[]{600, 180};
            case BUILDING: return new double[]{8, 2};
            default: return new double[]{40, 10};
        }
    }

    private static Entry e(String display, String key, Category cat, double buy, double sell) {
        return new Entry(display, key, cat, buy, sell);
    }

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final Entry[] ALL = addFamilies(new Entry[] {
            // Building - 1.8 baseline, then every wood and block type as it arrives.
            e("Stone", "stone", Category.BUILDING, 10, 2),
            e("Cobblestone", "cobblestone", Category.BUILDING, 8, 2),
            e("Oak Planks", "oak_planks", Category.BUILDING, 12, 3),
            e("Spruce Planks", "spruce_planks", Category.BUILDING, 14, 3),
            e("Birch Planks", "birch_planks", Category.BUILDING, 14, 3),
            e("Jungle Planks", "jungle_planks", Category.BUILDING, 14, 3),
            e("Acacia Planks", "acacia_planks", Category.BUILDING, 14, 3),
            e("Dark Oak Planks", "dark_oak_planks", Category.BUILDING, 16, 4),
            e("Glass", "glass", Category.BUILDING, 14, 3),
            e("Oak Log", "oak_log", Category.BUILDING, 20, 5),
            e("Spruce Log", "spruce_log", Category.BUILDING, 22, 5),
            e("Birch Log", "birch_log", Category.BUILDING, 22, 5),
            e("Jungle Log", "jungle_log", Category.BUILDING, 22, 5),
            e("Acacia Log", "acacia_log", Category.BUILDING, 24, 6),
            e("Dark Oak Log", "dark_oak_log", Category.BUILDING, 24, 6),
            e("Sandstone", "sandstone", Category.BUILDING, 12, 3),
            e("Bricks", "bricks", Category.BUILDING, 30, 8),
            e("Netherrack", "netherrack", Category.BUILDING, 10, 2),
            e("End Stone", "end_stone", Category.BUILDING, 20, 5),
            e("Wool", "wool", Category.BUILDING, 10, 2),
            e("White Terracotta", "terracotta", Category.BUILDING, 15, 3),
            e("Bookshelf", "bookshelf", Category.BUILDING, 120, 30),
            e("Prismarine", "prismarine", Category.BUILDING, 180, 45),
            e("Sea Lantern", "sea_lantern", Category.BUILDING, 350, 90),
            e("Concrete", "concrete", Category.BUILDING, 12, 3),
            e("Blue Ice", "blue_ice", Category.BUILDING, 260, 70),

            // Tools & weapons.
            e("Wooden Sword", "wooden_sword", Category.TOOLS, 25, 5),
            e("Stone Sword", "stone_sword", Category.TOOLS, 50, 12),
            e("Iron Sword", "iron_sword", Category.TOOLS, 260, 70),
            e("Golden Sword", "golden_sword", Category.TOOLS, 180, 45),
            e("Diamond Sword", "diamond_sword", Category.TOOLS, 1500, 500),
            e("Wooden Pickaxe", "wooden_pickaxe", Category.TOOLS, 25, 5),
            e("Stone Pickaxe", "stone_pickaxe", Category.TOOLS, 50, 12),
            e("Iron Pickaxe", "iron_pickaxe", Category.TOOLS, 220, 60),
            e("Diamond Pickaxe", "diamond_pickaxe", Category.TOOLS, 1400, 450),
            e("Stone Axe", "stone_axe", Category.TOOLS, 40, 8),
            e("Iron Axe", "iron_axe", Category.TOOLS, 200, 55),
            e("Diamond Axe", "diamond_axe", Category.TOOLS, 1300, 420),
            e("Iron Shovel", "iron_shovel", Category.TOOLS, 90, 22),
            e("Diamond Shovel", "diamond_shovel", Category.TOOLS, 700, 220),
            e("Iron Hoe", "iron_hoe", Category.TOOLS, 150, 35),
            e("Bow", "bow", Category.TOOLS, 120, 30),
            e("Arrow", "arrow", Category.TOOLS, 6, 1),
            e("Fishing Rod", "fishing_rod", Category.TOOLS, 90, 20),
            e("Shears", "shears", Category.TOOLS, 80, 20),
            e("Carrot on a Stick", "carrot_on_a_stick", Category.TOOLS, 110, 25),
            e("Shield", "shield", Category.TOOLS, 150, 40),
            e("Crossbow", "crossbow", Category.TOOLS, 600, 180),
            e("Trident", "trident", Category.TOOLS, 5000, 1600),
            e("Spyglass", "spyglass", Category.TOOLS, 400, 120),
            e("Mace", "mace", Category.TOOLS, 8000, 2600),
            e("Brush", "brush", Category.TOOLS, 240, 70),

            // Armor & combat.
            e("Leather Helmet", "leather_helmet", Category.ARMOR, 40, 10),
            e("Leather Chestplate", "leather_chestplate", Category.ARMOR, 60, 15),
            e("Leather Leggings", "leather_leggings", Category.ARMOR, 55, 13),
            e("Leather Boots", "leather_boots", Category.ARMOR, 35, 8),
            e("Iron Helmet", "iron_helmet", Category.ARMOR, 180, 50),
            e("Iron Chestplate", "iron_chestplate", Category.ARMOR, 300, 85),
            e("Iron Leggings", "iron_leggings", Category.ARMOR, 260, 75),
            e("Iron Boots", "iron_boots", Category.ARMOR, 160, 45),
            e("Diamond Helmet", "diamond_helmet", Category.ARMOR, 1200, 380),
            e("Diamond Chestplate", "diamond_chestplate", Category.ARMOR, 1600, 500),
            e("Diamond Leggings", "diamond_leggings", Category.ARMOR, 1400, 450),
            e("Diamond Boots", "diamond_boots", Category.ARMOR, 1100, 350),
            e("Gold Helmet", "golden_helmet", Category.ARMOR, 160, 40),
            e("Gold Chestplate", "golden_chestplate", Category.ARMOR, 250, 65),
            e("Shulker Box", "shulker_box", Category.ARMOR, 3000, 900),
            e("Totem of Undying", "totem", Category.ARMOR, 5000, 1500),
            e("Elytra", "elytra", Category.ARMOR, 8000, 2500),
            e("Netherite Sword", "netherite_sword", Category.ARMOR, 9000, 3000),
            e("Netherite Pickaxe", "netherite_pickaxe", Category.ARMOR, 8500, 2800),
            e("Netherite Helmet", "netherite_helmet", Category.ARMOR, 7000, 2300),
            e("Netherite Chestplate", "netherite_chestplate", Category.ARMOR, 9000, 3000),
            e("Netherite Leggings", "netherite_leggings", Category.ARMOR, 8000, 2600),
            e("Netherite Boots", "netherite_boots", Category.ARMOR, 6500, 2200),

            // Food.
            e("Apple", "apple", Category.FOOD, 20, 5),
            e("Golden Apple", "golden_apple", Category.FOOD, 400, 120),
            e("Enchanted Golden Apple", "enchanted_golden_apple", Category.FOOD, 6000, 2000),
            e("Bread", "bread", Category.FOOD, 15, 4),
            e("Cooked Beef", "cooked_beef", Category.FOOD, 30, 8),
            e("Cooked Porkchop", "cooked_porkchop", Category.FOOD, 30, 8),
            e("Cooked Chicken", "cooked_chicken", Category.FOOD, 25, 6),
            e("Cooked Mutton", "cooked_mutton", Category.FOOD, 25, 6),
            e("Cooked Rabbit", "cooked_rabbit", Category.FOOD, 28, 7),
            e("Cooked Cod", "cooked_cod", Category.FOOD, 30, 8),
            e("Cooked Salmon", "cooked_salmon", Category.FOOD, 35, 9),
            e("Cake", "cake", Category.FOOD, 120, 30),
            e("Cookie", "cookie", Category.FOOD, 8, 2),
            e("Melon", "melon", Category.FOOD, 8, 2),
            e("Carrot", "carrot", Category.FOOD, 12, 3),
            e("Potato", "potato", Category.FOOD, 12, 3),
            e("Baked Potato", "baked_potato", Category.FOOD, 18, 4),
            e("Pumpkin Pie", "pumpkin_pie", Category.FOOD, 50, 12),
            e("Golden Carrot", "golden_carrot", Category.FOOD, 90, 25),
            e("Sweet Berries", "sweet_berries", Category.FOOD, 15, 3),
            e("Glow Berries", "glow_berries", Category.FOOD, 18, 4),
            e("Dried Kelp", "dried_kelp", Category.FOOD, 14, 3),
            e("Honey Bottle", "honey_bottle", Category.FOOD, 60, 15),

            // Ores & materials.
            e("Coal", "coal", Category.ORES, 20, 5),
            e("Iron Ingot", "iron_ingot", Category.ORES, 100, 40),
            e("Gold Ingot", "gold_ingot", Category.ORES, 200, 80),
            e("Diamond", "diamond", Category.ORES, 500, 200),
            e("Emerald", "emerald", Category.ORES, 600, 240),
            e("Lapis Lazuli", "lapis", Category.ORES, 60, 20),
            e("Quartz", "quartz", Category.ORES, 90, 30),
            e("Iron Ore", "iron_ore", Category.ORES, 80, 30),
            e("Gold Ore", "gold_ore", Category.ORES, 160, 60),
            e("Diamond Ore", "diamond_ore", Category.ORES, 400, 160),
            e("Emerald Ore", "emerald_ore", Category.ORES, 450, 180),
            e("Copper Ingot", "copper_ingot", Category.ORES, 70, 25),
            e("Copper Block", "copper_block", Category.ORES, 640, 220),
            e("Raw Iron", "raw_iron", Category.ORES, 60, 22),
            e("Raw Copper", "raw_copper", Category.ORES, 45, 15),
            e("Raw Gold", "raw_gold", Category.ORES, 140, 50),
            e("Amethyst Shard", "amethyst_shard", Category.ORES, 120, 35),
            e("Netherite Ingot", "netherite_ingot", Category.ORES, 3000, 1000),
            e("Netherite Scrap", "netherite_scrap", Category.ORES, 800, 250),
            e("Ancient Debris", "ancient_debris", Category.ORES, 1500, 500),

            // Redstone & mechanics.
            e("Redstone", "redstone", Category.REDSTONE, 15, 4),
            e("Redstone Torch", "redstone_torch", Category.REDSTONE, 20, 5),
            e("Piston", "piston", Category.REDSTONE, 80, 20),
            e("Hopper", "hopper", Category.REDSTONE, 250, 70),
            e("Comparator", "comparator", Category.REDSTONE, 60, 15),
            e("Repeater", "repeater", Category.REDSTONE, 55, 13),
            e("Redstone Lamp", "redstone_lamp", Category.REDSTONE, 65, 16),
            e("Lever", "lever", Category.REDSTONE, 12, 3),
            e("Stone Pressure Plate", "stone_pressure_plate", Category.REDSTONE, 30, 7),
            e("Daylight Detector", "daylight_detector", Category.REDSTONE, 90, 24),
            e("Observer", "observer", Category.REDSTONE, 90, 25),
            e("Target", "target", Category.REDSTONE, 140, 40),
            e("Sculk Sensor", "sculk_sensor", Category.REDSTONE, 550, 150),
            e("Calibrated Sculk Sensor", "calibrated_sculk_sensor", Category.REDSTONE, 750, 210),
            e("Crafter", "crafter", Category.REDSTONE, 400, 110),
            e("Copper Bulb", "copper_bulb", Category.REDSTONE, 300, 90),

            // Decoration.
            e("Flower Pot", "flower_pot", Category.DECORATION, 20, 5),
            e("Item Frame", "item_frame", Category.DECORATION, 30, 8),
            e("Torch", "torch", Category.DECORATION, 6, 1),
            e("Glowstone", "glowstone", Category.DECORATION, 40, 10),
            e("Painting", "painting", Category.DECORATION, 80, 20),
            e("Jack o'Lantern", "jack_o_lantern", Category.DECORATION, 50, 12),
            e("Anvil", "anvil", Category.DECORATION, 1500, 500),
            e("Enchanting Table", "enchanting_table", Category.DECORATION, 2800, 900),
            e("Banner", "banner", Category.DECORATION, 40, 10),
            e("Lantern", "lantern", Category.DECORATION, 35, 8),
            e("Candle", "candle", Category.DECORATION, 20, 5),
            e("Glow Item Frame", "glow_item_frame", Category.DECORATION, 45, 12),
            e("Amethyst Block", "amethyst_block", Category.DECORATION, 500, 150),
            e("Soul Lantern", "soul_lantern", Category.DECORATION, 45, 12),

            // Transport.
            e("Rail", "rail", Category.TRANSPORT, 25, 6),
            e("Powered Rail", "powered_rail", Category.TRANSPORT, 120, 30),
            e("Detector Rail", "detector_rail", Category.TRANSPORT, 110, 28),
            e("Activator Rail", "activator_rail", Category.TRANSPORT, 110, 28),
            e("Minecart", "minecart", Category.TRANSPORT, 300, 90),
            e("Chest Minecart", "chest_minecart", Category.TRANSPORT, 350, 110),
            e("Furnace Minecart", "furnace_minecart", Category.TRANSPORT, 400, 120),
            e("TNT Minecart", "tnt_minecart", Category.TRANSPORT, 450, 130),
            e("Saddle", "saddle", Category.TRANSPORT, 300, 90),
            e("Oak Boat", "boat", Category.TRANSPORT, 40, 10),
            e("Spruce Boat", "spruce_boat", Category.TRANSPORT, 45, 11),
            e("Birch Boat", "birch_boat", Category.TRANSPORT, 45, 11),
            e("Mangrove Boat", "mangrove_boat", Category.TRANSPORT, 50, 12),
            e("Cherry Boat", "cherry_boat", Category.TRANSPORT, 50, 12),

            // Nature.
            e("Oak Sapling", "oak_sapling", Category.NATURE, 15, 3),
            e("Spruce Sapling", "spruce_sapling", Category.NATURE, 16, 4),
            e("Birch Sapling", "birch_sapling", Category.NATURE, 16, 4),
            e("Jungle Sapling", "jungle_sapling", Category.NATURE, 17, 4),
            e("Acacia Sapling", "acacia_sapling", Category.NATURE, 17, 4),
            e("Dark Oak Sapling", "dark_oak_sapling", Category.NATURE, 18, 4),
            e("Cherry Sapling", "cherry_sapling", Category.NATURE, 25, 6),
            e("Mangrove Propagule", "mangrove_propagule", Category.NATURE, 25, 6),
            e("Wheat Seeds", "wheat_seeds", Category.NATURE, 8, 2),
            e("Bone Meal", "bone_meal", Category.NATURE, 12, 3),
            e("Dirt", "dirt", Category.NATURE, 5, 1),
            e("Grass Block", "grass_block", Category.NATURE, 8, 2),
            e("Sugar Cane", "sugar_cane", Category.NATURE, 15, 3),
            e("Cactus", "cactus", Category.NATURE, 18, 4),
            e("Vine", "vine", Category.NATURE, 20, 5),
            e("Lily Pad", "lily_pad", Category.NATURE, 14, 3),
            e("Sunflower", "sunflower", Category.NATURE, 30, 8),
            e("Pumpkin", "pumpkin", Category.NATURE, 30, 8),
            e("Sea Pickle", "sea_pickle", Category.NATURE, 25, 6),
            e("Kelp", "kelp", Category.NATURE, 16, 4),

            // Brewing & alchemy.
            e("Glass Bottle", "glass_bottle", Category.BREWING, 15, 3),
            e("Brewing Stand", "brewing_stand", Category.BREWING, 190, 55),
            e("Cauldron", "cauldron", Category.BREWING, 150, 42),
            e("Blaze Powder", "blaze_powder", Category.BREWING, 80, 20),
            e("Fermented Spider Eye", "fermented_spider_eye", Category.BREWING, 60, 15),
            e("Nether Wart", "nether_wart", Category.BREWING, 40, 10),
            e("Magma Cream", "magma_cream", Category.BREWING, 140, 40),
            e("Glistering Melon", "glistering_melon", Category.BREWING, 90, 25),
            e("Ghast Tear", "ghast_tear", Category.BREWING, 350, 100),
            e("Dragon Breath", "dragon_breath", Category.BREWING, 600, 180),
            e("Splash Potion", "splash_potion", Category.BREWING, 200, 60),
            e("Lingering Potion", "lingering_potion", Category.BREWING, 250, 75),
            e("Honeycomb", "honeycomb", Category.BREWING, 80, 22),

            // Enchanted / special.
            e("Bottle o' Enchanting", "experience_bottle", Category.ENCHANTED, 200, 60),
            e("Ender Pearl", "ender_pearl", Category.ENCHANTED, 150, 40),
            e("Ender Eye", "ender_eye", Category.ENCHANTED, 250, 75),
            e("Enchanted Book", "enchanted_book", Category.ENCHANTED, 500, 150),
            e("Name Tag", "name_tag", Category.MISC, 100, 25),
            e("Book", "book", Category.MISC, 30, 8),
            e("Paper", "paper", Category.MISC, 8, 2),
            e("String", "string", Category.MISC, 10, 2),
            e("Slime Ball", "slime_ball", Category.MISC, 140, 40),
            e("Bone", "bone", Category.MISC, 30, 8),
            e("Gunpowder", "gunpowder", Category.MISC, 70, 18),
            e("Leather", "leather", Category.MISC, 60, 16),
            e("Nether Star", "nether_star", Category.MISC, 6000, 2000),
            e("Beacon", "beacon", Category.MISC, 10000, 3500),
            e("Firework Rocket", "firework_rocket", Category.MISC, 60, 14),
            e("End Crystal", "end_crystal", Category.MISC, 1800, 550),
            e("Recovery Compass", "recovery_compass", Category.MISC, 900, 280)
    });

    /** Appends the big-shop families and extra stock on top of the base catalog. */
    private static Entry[] addFamilies(Entry[] base) {
        List<Entry> list = new ArrayList<Entry>();
        for (Entry entry : base) {
            list.add(entry);
        }

        // Color families: every dye of wool, carpet, terracotta, concrete, glass...
        colors(list, "Wool", "wool", Category.BUILDING, 10, 3);
        colors(list, "Carpet", "carpet", Category.BUILDING, 8, 2);
        colors(list, "Terracotta", "terracotta", Category.BUILDING, 15, 4);
        colors(list, "Concrete", "concrete", Category.BUILDING, 13, 4);
        colors(list, "Concrete Powder", "concrete_powder", Category.BUILDING, 12, 3);
        colors(list, "Stained Glass", "stained_glass", Category.BUILDING, 12, 3);
        colors(list, "Stained Glass Pane", "glass_pane", Category.BUILDING, 8, 2);
        colors(list, "Bed", "bed", Category.BUILDING, 90, 25);
        colors(list, "Shulker Box", "shulker_box", Category.ARMOR, 3200, 950);

        // Every wood's stairs, slabs, fences, gates, doors and trapdoors.
        wood(list, "oak", 25);
        wood(list, "spruce", 28);
        wood(list, "birch", 28);
        wood(list, "jungle", 30);
        wood(list, "acacia", 30);
        wood(list, "dark_oak", 34);
        wood(list, "mangrove", 40);
        wood(list, "cherry", 45);
        wood(list, "bamboo", 30);

        // Masonry, stone and nether blocks.
        s(list, "Stone Bricks", "stone_bricks", Category.BUILDING, 30, 8);
        s(list, "Stone Brick Stairs", "stone_brick_stairs", Category.BUILDING, 40, 10);
        s(list, "Cobblestone Stairs", "cobblestone_stairs", Category.BUILDING, 25, 6);
        s(list, "Stone Slab", "stone_slab", Category.BUILDING, 15, 4);
        s(list, "Polished Andesite", "polished_andesite", Category.BUILDING, 14, 3);
        s(list, "Polished Diorite", "polished_diorite", Category.BUILDING, 14, 3);
        s(list, "Polished Granite", "polished_granite", Category.BUILDING, 14, 3);
        s(list, "Quartz Block", "quartz_block", Category.BUILDING, 100, 28);
        s(list, "Quartz Stairs", "quartz_stairs", Category.BUILDING, 120, 32);
        s(list, "Smooth Quartz", "smooth_quartz", Category.BUILDING, 110, 30);
        s(list, "Purpur Block", "purpur_block", Category.BUILDING, 45, 12);
        s(list, "Purpur Stairs", "purpur_stairs", Category.BUILDING, 50, 13);
        s(list, "Purpur Pillar", "purpur_pillar", Category.BUILDING, 48, 12);
        s(list, "End Stone Bricks", "end_stone_bricks", Category.BUILDING, 25, 6);
        s(list, "Crimson Planks", "crimson_planks", Category.BUILDING, 18, 4);
        s(list, "Warped Planks", "warped_planks", Category.BUILDING, 18, 4);
        s(list, "Crimson Stem", "crimson_stem", Category.BUILDING, 30, 7);
        s(list, "Warped Stem", "warped_stem", Category.BUILDING, 30, 7);
        s(list, "Crimson Fungus", "crimson_fungus", Category.BUILDING, 35, 9);
        s(list, "Warped Fungus", "warped_fungus", Category.BUILDING, 35, 9);
        s(list, "Nether Wart Block", "nether_wart_block", Category.BUILDING, 65, 17);
        s(list, "Warped Wart Block", "warped_wart_block", Category.BUILDING, 65, 17);
        s(list, "Shroomlight", "shroomlight", Category.BUILDING, 130, 35);
        s(list, "Basalt", "basalt", Category.BUILDING, 26, 7);
        s(list, "Blackstone", "blackstone", Category.BUILDING, 24, 6);
        s(list, "Polished Blackstone", "polished_blackstone", Category.BUILDING, 30, 8);
        s(list, "Crying Obsidian", "crying_obsidian", Category.BUILDING, 550, 180);
        s(list, "Respawn Anchor", "respawn_anchor", Category.BUILDING, 850, 280);
        s(list, "Lodestone", "lodestone", Category.BUILDING, 1200, 390);
        s(list, "Soul Sand", "soul_sand", Category.BUILDING, 12, 3);
        s(list, "Soul Soil", "soul_soil", Category.BUILDING, 16, 4);
        s(list, "Deepslate", "deepslate", Category.BUILDING, 22, 5);
        s(list, "Cobbled Deepslate", "cobbled_deepslate", Category.BUILDING, 20, 5);
        s(list, "Deepslate Bricks", "deepslate_bricks", Category.BUILDING, 26, 7);
        s(list, "Deepslate Tiles", "deepslate_tiles", Category.BUILDING, 28, 7);
        s(list, "Calcite", "calcite", Category.BUILDING, 60, 16);
        s(list, "Tuff", "tuff", Category.BUILDING, 22, 6);
        s(list, "Budding Amethyst", "budding_amethyst", Category.BUILDING, 420, 130);
        s(list, "Tinted Glass", "tinted_glass", Category.BUILDING, 170, 45);
        s(list, "Mud", "mud", Category.BUILDING, 6, 2);
        s(list, "Packed Mud", "packed_mud", Category.BUILDING, 18, 5);
        s(list, "Mud Bricks", "mud_bricks", Category.BUILDING, 22, 6);
        s(list, "Mangrove Log", "mangrove_log", Category.BUILDING, 28, 7);
        s(list, "Mangrove Planks", "mangrove_planks", Category.BUILDING, 18, 4);
        s(list, "Mangrove Roots", "mangrove_roots", Category.BUILDING, 26, 7);
        s(list, "Cherry Log", "cherry_log", Category.BUILDING, 28, 7);
        s(list, "Cherry Planks", "cherry_planks", Category.BUILDING, 18, 4);
        s(list, "Bamboo Block", "bamboo_block", Category.BUILDING, 45, 12);
        s(list, "Bamboo Planks", "bamboo_planks", Category.BUILDING, 20, 5);
        s(list, "Bamboo Mosaic", "bamboo_mosaic", Category.BUILDING, 22, 6);
        s(list, "Moss Block", "moss_block", Category.BUILDING, 24, 6);
        s(list, "Moss Carpet", "moss_carpet", Category.BUILDING, 16, 4);
        s(list, "Azalea", "azalea", Category.BUILDING, 30, 8);
        s(list, "Flowering Azalea", "flowering_azalea", Category.BUILDING, 45, 12);
        s(list, "Big Dripleaf", "big_dripleaf", Category.BUILDING, 32, 8);
        s(list, "Spore Blossom", "spore_blossom", Category.BUILDING, 42, 11);
        s(list, "Glow Lichen", "glow_lichen", Category.BUILDING, 28, 7);

        // Vanilla heads - real decorative skulls.
        s(list, "Skeleton Skull", "skeleton_skull", Category.DECORATION, 120, 35);
        s(list, "Wither Skeleton Skull", "wither_skeleton_skull", Category.DECORATION, 250, 70);
        s(list, "Zombie Head", "zombie_head", Category.DECORATION, 120, 35);
        s(list, "Creeper Head", "creeper_head", Category.DECORATION, 120, 35);
        s(list, "Dragon Head", "dragon_head", Category.DECORATION, 400, 120);
        s(list, "Piglin Head", "piglin_head", Category.DECORATION, 200, 60);

        // Compact metal blocks.
        s(list, "Iron Block", "iron_block", Category.ORES, 900, 320);
        s(list, "Gold Block", "gold_block", Category.ORES, 1800, 640);
        s(list, "Diamond Block", "diamond_block", Category.ORES, 4500, 1700);
        s(list, "Emerald Block", "emerald_block", Category.ORES, 5400, 1900);
        s(list, "Lapis Block", "lapis_block", Category.ORES, 600, 210);
        s(list, "Coal Block", "coal_block", Category.ORES, 200, 70);
        s(list, "Netherite Block", "netherite_block", Category.ORES, 28000, 9500);
        s(list, "Raw Iron Block", "raw_iron_block", Category.ORES, 620, 210);
        s(list, "Raw Copper Block", "raw_copper_block", Category.ORES, 420, 145);
        s(list, "Raw Gold Block", "raw_gold_block", Category.ORES, 1440, 480);

        // Redstone & utility.
        s(list, "Redstone Block", "redstone_block", Category.REDSTONE, 190, 55);
        s(list, "Bell", "bell", Category.REDSTONE, 950, 300);
        s(list, "Lectern", "lectern", Category.REDSTONE, 380, 105);
        s(list, "Jukebox", "jukebox", Category.REDSTONE, 950, 300);
        s(list, "Note Block", "note_block", Category.REDSTONE, 260, 72);
        s(list, "Dispenser", "dispenser", Category.REDSTONE, 280, 78);
        s(list, "Dropper", "dropper", Category.REDSTONE, 290, 80);
        s(list, "Trapped Chest", "trapped_chest", Category.REDSTONE, 300, 85);
        s(list, "Blast Furnace", "blast_furnace", Category.REDSTONE, 380, 105);
        s(list, "Smoker", "smoker", Category.REDSTONE, 330, 92);
        s(list, "Stonecutter", "stonecutter", Category.REDSTONE, 220, 60);
        s(list, "Campfire", "campfire", Category.REDSTONE, 130, 35);
        s(list, "Soul Campfire", "soul_campfire", Category.REDSTONE, 160, 42);
        s(list, "Lightning Rod", "lightning_rod", Category.REDSTONE, 140, 38);
        s(list, "Scaffolding", "scaffolding", Category.REDSTONE, 90, 24);
        s(list, "Slime Block", "slime_block", Category.REDSTONE, 230, 62);
        s(list, "Honey Block", "honey_block", Category.REDSTONE, 250, 68);
        s(list, "TNT", "tnt", Category.REDSTONE, 240, 64);

        // Decoration extra.
        s(list, "Chain", "chain", Category.DECORATION, 45, 12);

        // More tools & drops.
        s(list, "Stick", "stick", Category.TOOLS, 6, 1);
        s(list, "Flint", "flint", Category.TOOLS, 55, 14);

        // More food.
        s(list, "Mushroom Stew", "mushroom_stew", Category.FOOD, 25, 6);
        s(list, "Rabbit Stew", "rabbit_stew", Category.FOOD, 30, 8);
        s(list, "Beetroot", "beetroot", Category.FOOD, 14, 4);
        s(list, "Beetroot Soup", "beetroot_soup", Category.FOOD, 22, 6);
        s(list, "Suspicious Stew", "suspicious_stew", Category.FOOD, 90, 23);
        s(list, "Pufferfish", "pufferfish", Category.FOOD, 160, 45);
        s(list, "Tropical Fish", "tropical_fish", Category.FOOD, 90, 25);
        s(list, "Salmon", "salmon", Category.FOOD, 32, 8);
        s(list, "Cod", "cod", Category.FOOD, 28, 7);
        s(list, "Milk Bucket", "milk_bucket", Category.FOOD, 35, 9);
        s(list, "Chorus Fruit", "chorus_fruit", Category.FOOD, 45, 12);
        s(list, "Popped Chorus Fruit", "popped_chorus_fruit", Category.FOOD, 65, 17);

        // Flowers for nature and decoration.
        s(list, "Dandelion", "dandelion", Category.NATURE, 30, 8);
        s(list, "Poppy", "poppy", Category.NATURE, 30, 8);
        s(list, "Blue Orchid", "blue_orchid", Category.NATURE, 32, 8);
        s(list, "Allium", "allium", Category.NATURE, 32, 8);
        s(list, "Azure Bluet", "azure_bluet", Category.NATURE, 32, 8);
        s(list, "Red Tulip", "red_tulip", Category.NATURE, 30, 8);
        s(list, "Orange Tulip", "orange_tulip", Category.NATURE, 30, 8);
        s(list, "White Tulip", "white_tulip", Category.NATURE, 30, 8);
        s(list, "Pink Tulip", "pink_tulip", Category.NATURE, 30, 8);
        s(list, "Oxeye Daisy", "oxeye_daisy", Category.NATURE, 32, 8);
        s(list, "Cornflower", "cornflower", Category.NATURE, 35, 9);
        s(list, "Lily of the Valley", "lily_of_the_valley", Category.NATURE, 35, 9);
        s(list, "Wither Rose", "wither_rose", Category.NATURE, 50, 13);
        s(list, "Torchflower", "torchflower", Category.NATURE, 45, 12);
        s(list, "Rose Bush", "rose_bush", Category.NATURE, 95, 26);
        s(list, "Lilac", "lilac", Category.NATURE, 95, 26);
        s(list, "Peony", "peony", Category.NATURE, 95, 26);
        s(list, "Sculk", "sculk", Category.NATURE, 30, 8);
        s(list, "Sculk Catalyst", "sculk_catalyst", Category.NATURE, 260, 75);
        s(list, "Sculk Shrieker", "sculk_shrieker", Category.NATURE, 300, 85);
        s(list, "Sculk Vein", "sculk_vein", Category.NATURE, 40, 10);

        // Brewing pickups and misc drops.
        s(list, "Spider Eye", "spider_eye", Category.BREWING, 45, 12);
        s(list, "Sugar", "sugar", Category.BREWING, 38, 10);
        s(list, "Glowstone Dust", "glowstone_dust", Category.BREWING, 26, 7);
        s(list, "Blaze Rod", "blaze_rod", Category.BREWING, 160, 45);
        s(list, "Feather", "feather", Category.MISC, 25, 6);
        s(list, "Rotten Flesh", "rotten_flesh", Category.MISC, 9, 2);
        s(list, "Shulker Shell", "shulker_shell", Category.MISC, 750, 240);
        s(list, "Phantom Membrane", "phantom_membrane", Category.MISC, 220, 60);
        s(list, "Prismarine Shard", "prismarine_shard", Category.MISC, 95, 26);
        s(list, "Prismarine Crystals", "prismarine_crystals", Category.MISC, 115, 32);
        s(list, "Nautilus Shell", "nautilus_shell", Category.MISC, 280, 78);
        s(list, "Heart of the Sea", "heart_of_the_sea", Category.MISC, 950, 310);
        s(list, "Conduit", "conduit", Category.MISC, 1300, 420);
        s(list, "Turtle Scute", "turtle_scute", Category.MISC, 320, 90);

        return list.toArray(new Entry[0]);
    }

    private static void colors(List<Entry> list, String title, String family, Category category,
                               double buy, double sell) {
        for (String color : COLORS) {
            s(list, title(color) + " " + title, color + "_" + family, category, buy, sell);
        }
    }

    private static void wood(List<Entry> list, String wood, double stairs) {
        String display = title(wood);
        double sell = stairs / 3;
        s(list, display + " Stairs", wood + "_stairs", Category.BUILDING, stairs, sell);
        s(list, display + " Slab", wood + "_slab", Category.BUILDING, stairs / 2, sell / 2);
        s(list, display + " Fence", wood + "_fence", Category.BUILDING, stairs / 2, sell / 2);
        s(list, display + " Fence Gate", wood + "_fence_gate", Category.BUILDING, stairs, sell);
        s(list, display + " Door", wood + "_door", Category.BUILDING, stairs * 3, sell * 3);
        s(list, display + " Trapdoor", wood + "_trapdoor", Category.BUILDING, stairs * 2, sell * 2);
    }

    private static void s(List<Entry> list, String display, String key, Category category,
                          double buy, double sell) {
        list.add(e(display, key, category, buy, sell));
    }

    /** "dark_oak" -> "Dark Oak". */
    private static String title(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}