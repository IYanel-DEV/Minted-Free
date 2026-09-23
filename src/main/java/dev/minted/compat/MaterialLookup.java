package dev.minted.compat;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The version-aware item registry: every material key the plugin can hand out,
 * with the earliest Minecraft minor release (on the 1.x line) that introduced
 * it and the enum names it can wear on each era of server.
 *
 * <p>Resolution is by name (modern spelling first, legacy fallbacks after), so
 * on 1.13+ the modern constant wins and on 1.8-1.12 the pre-rename one does.
 * A key only resolves when BOTH the running server is at least {@code since}
 * and one of its names exists - so a 1.8 server never yields netherite and an
 * 1.26 server yields everything above. Version-gated lookups also exist for
 * {@link dev.minted.integration.via.ViaVersionHook}: on a server with ViaVersion
 * a low-version client can be given only the items their own client knows,
 * while a modern client keeps the full catalog. Compiles against 1.8 because
 * no modern material constant is referenced directly.
 */
public final class MaterialLookup {

    /**
     * One registry row: the version that introduced the key ({@code since},
     * on the 1.x line) and the candidate enum names, modern first.
     *
     * <p>The parallel {@code data} array holds the legacy durability value a
     * pre-1.13 name needs to stay distinct (dye index for wools/beds/terracotta,
     * wood index for planks/stairs/doors, skull type for heads...). The modern
     * name(s) always carry data 0 - on 1.13+ every variant is its own constant,
     * so only the pre-rename era needs the extra bits. This is what keeps red
     * wool red, a creeper head a creeper, and a spruce door spruce on 1.8-1.12.
     */
    public static final class Entry {

        private final String key;
        private final int since;
        private final String[] names;
        private final short[] data;

        Entry(String key, int since, String[] names, short[] data) {
            this.key = key;
            this.since = since;
            this.names = names;
            this.data = data;
        }

        public String key() {
            return key;
        }

        public int since() {
            return since;
        }

        public String[] names() {
            return names;
        }

        /** The legacy durability for the name at {@code index} (0 for modern-era names). */
        public short dataAt(int index) {
            return data[index];
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();

    private static void put(String key, int since, String... names) {
        ENTRIES.put(key, new Entry(key, since, names, new short[names.length]));
    }

    /**
     * Registers a key whose pre-1.13 name(s) need a legacy durability value to
     * stay distinct from the family's base variant (dye colour, wood type,
     * skull type...). The modern first name still gets data 0.
     */
    private static void putD(String key, int since, int legacyData, String... names) {
        short[] data = new short[names.length];
        for (int i = 1; i < data.length; i++) {
            data[i] = (short) legacyData;
        }
        ENTRIES.put(key, new Entry(key, since, names, data));
    }

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final String[] COLOR_NAMES = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
            "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };

    /** One registry row per dye color of a family (modern {@code COLOR_postfix}). */
    private static void colorFamily(String family, int since, String modern, String legacy) {
        for (int i = 0; i < COLORS.length; i++) {
            putD(COLORS[i] + "_" + family, since, i, COLOR_NAMES[i] + "_" + modern, legacy);
        }
    }

    /** stairs, slab, fence, gate, door and trapdoor for one wood type. */
    private static void woodFamily(String wood, int since, int legacyWoodIndex) {
        String upper = wood.toUpperCase(Locale.ROOT);
        putD(wood + "_stairs", since, legacyWoodIndex, upper + "_STAIRS", "WOOD_STAIRS");
        putD(wood + "_slab", since, legacyWoodIndex, upper + "_SLAB", "WOOD_STEP");
        putD(wood + "_fence", since, legacyWoodIndex, upper + "_FENCE", "FENCE");
        putD(wood + "_fence_gate", since, legacyWoodIndex, upper + "_FENCE_GATE", "FENCE_GATE");
        putD(wood + "_door", since, legacyWoodIndex, upper + "_DOOR", "WOODEN_DOOR", "WOOD_DOOR");
        putD(wood + "_trapdoor", since, legacyWoodIndex, upper + "_TRAPDOOR", "TRAP_DOOR");
    }

    static {
        // --- 1.8: the classic baseline. Modern names first, pre-1.13 names after. ---
        // Building.
        put("stone", 8, "STONE");
        put("cobblestone", 8, "COBBLESTONE");
        putD("oak_planks", 8, 0, "OAK_PLANKS", "WOOD");
        putD("spruce_planks", 8, 1, "SPRUCE_PLANKS", "WOOD");
        putD("birch_planks", 8, 2, "BIRCH_PLANKS", "WOOD");
        putD("jungle_planks", 8, 3, "JUNGLE_PLANKS", "WOOD");
        putD("acacia_planks", 8, 4, "ACACIA_PLANKS", "WOOD");
        putD("dark_oak_planks", 8, 5, "DARK_OAK_PLANKS", "WOOD");
        put("glass", 8, "GLASS");
        putD("oak_log", 8, 0, "OAK_LOG", "LOG");
        putD("spruce_log", 8, 1, "SPRUCE_LOG", "LOG");
        putD("birch_log", 8, 2, "BIRCH_LOG", "LOG");
        putD("jungle_log", 8, 3, "JUNGLE_LOG", "LOG");
        putD("acacia_log", 8, 4, "ACACIA_LOG", "LOG_2");
        putD("dark_oak_log", 8, 5, "DARK_OAK_LOG", "LOG_2");
        put("sandstone", 8, "SANDSTONE");
        put("bricks", 8, "BRICKS", "BRICK");
        put("netherrack", 8, "NETHERRACK");
        put("end_stone", 8, "END_STONE");
        put("wool", 8, "WHITE_WOOL", "WOOL");
        put("terracotta", 8, "WHITE_TERRACOTTA", "STAINED_CLAY");
        put("bookshelf", 8, "BOOKSHELF");
        put("prismarine", 8, "PRISMARINE");
        put("sea_lantern", 8, "SEA_LANTERN");
        put("gravel", 8, "GRAVEL");
        put("obsidian", 8, "OBSIDIAN");
        put("smooth_stone", 8, "SMOOTH_STONE", "SMOOTH_STONE");

        // Tools & weapons.
        put("wooden_sword", 8, "WOODEN_SWORD", "WOOD_SWORD");
        put("stone_sword", 8, "STONE_SWORD");
        put("iron_sword", 8, "IRON_SWORD");
        put("golden_sword", 8, "GOLDEN_SWORD", "GOLD_SWORD");
        put("diamond_sword", 8, "DIAMOND_SWORD");
        put("wooden_pickaxe", 8, "WOODEN_PICKAXE", "WOOD_PICKAXE");
        put("stone_pickaxe", 8, "STONE_PICKAXE");
        put("iron_pickaxe", 8, "IRON_PICKAXE");
        put("diamond_pickaxe", 8, "DIAMOND_PICKAXE");
        put("stone_axe", 8, "STONE_AXE");
        put("iron_axe", 8, "IRON_AXE");
        put("diamond_axe", 8, "DIAMOND_AXE");
        put("wooden_shovel", 8, "WOODEN_SHOVEL", "WOOD_SPADE");
        put("stone_shovel", 8, "STONE_SHOVEL", "STONE_SPADE");
        put("iron_shovel", 8, "IRON_SHOVEL", "IRON_SPADE");
        put("diamond_shovel", 8, "DIAMOND_SHOVEL", "DIAMOND_SPADE");
        put("wooden_hoe", 8, "WOODEN_HOE", "WOOD_HOE");
        put("iron_hoe", 8, "IRON_HOE");
        put("diamond_hoe", 8, "DIAMOND_HOE");
        put("bow", 8, "BOW");
        put("arrow", 8, "ARROW");
        put("fishing_rod", 8, "FISHING_ROD");
        put("shears", 8, "SHEARS");
        put("flint_and_steel", 8, "FLINT_AND_STEEL");
        put("carrot_on_a_stick", 8, "CARROT_ON_A_STICK", "CARROT_STICK");
        put("shield", 9, "SHIELD");

        // Armor & combat.
        put("leather_helmet", 8, "LEATHER_HELMET");
        put("leather_chestplate", 8, "LEATHER_CHESTPLATE");
        put("leather_leggings", 8, "LEATHER_LEGGINGS");
        put("leather_boots", 8, "LEATHER_BOOTS");
        put("iron_helmet", 8, "IRON_HELMET");
        put("iron_chestplate", 8, "IRON_CHESTPLATE");
        put("iron_leggings", 8, "IRON_LEGGINGS");
        put("iron_boots", 8, "IRON_BOOTS");
        put("diamond_helmet", 8, "DIAMOND_HELMET");
        put("diamond_chestplate", 8, "DIAMOND_CHESTPLATE");
        put("diamond_leggings", 8, "DIAMOND_LEGGINGS");
        put("diamond_boots", 8, "DIAMOND_BOOTS");
        put("golden_helmet", 8, "GOLDEN_HELMET", "GOLD_HELMET");
        put("golden_chestplate", 8, "GOLDEN_CHESTPLATE", "GOLD_CHESTPLATE");
        put("elytra", 9, "ELYTRA");
        put("shulker_box", 11, "SHULKER_BOX", "PURPLE_SHULKER_BOX");
        put("totem", 11, "TOTEM_OF_UNDYING");

        // Food.
        put("apple", 8, "APPLE");
        put("golden_apple", 8, "GOLDEN_APPLE");
        put("enchanted_golden_apple", 8, "ENCHANTED_GOLDEN_APPLE");
        put("bread", 8, "BREAD");
        put("cooked_beef", 8, "COOKED_BEEF");
        put("cooked_porkchop", 8, "COOKED_PORKCHOP", "GRILLED_PORK");
        put("cooked_chicken", 8, "COOKED_CHICKEN");
        put("cooked_mutton", 8, "COOKED_MUTTON");
        put("cooked_rabbit", 8, "COOKED_RABBIT");
        put("cooked_cod", 8, "COOKED_COD", "COOKED_FISH");
        put("cooked_salmon", 8, "COOKED_SALMON");
        put("cake", 8, "CAKE");
        put("cookie", 8, "COOKIE");
        put("melon", 8, "MELON");
        put("carrot", 8, "CARROT", "CARROT_ITEM");
        put("potato", 8, "POTATO", "POTATO_ITEM");
        put("baked_potato", 8, "BAKED_POTATO");
        put("pumpkin_pie", 8, "PUMPKIN_PIE");
        put("golden_carrot", 8, "GOLDEN_CARROT");
        put("sweet_berries", 14, "SWEET_BERRIES");
        put("glow_berries", 19, "GLOW_BERRIES");

        // Ores & materials.
        put("coal", 8, "COAL");
        put("charcoal", 8, "CHARCOAL");
        put("iron_ingot", 8, "IRON_INGOT");
        put("gold_ingot", 8, "GOLD_INGOT");
        put("diamond", 8, "DIAMOND");
        put("emerald", 8, "EMERALD");
        put("lapis", 8, "LAPIS_LAZULI", "INK_SACK");
        put("quartz", 8, "QUARTZ");
        put("iron_ore", 8, "IRON_ORE");
        put("gold_ore", 8, "GOLD_ORE");
        put("diamond_ore", 8, "DIAMOND_ORE");
        put("emerald_ore", 8, "EMERALD_ORE");
        put("copper_ingot", 17, "COPPER_INGOT");
        put("copper_block", 17, "COPPER_BLOCK");
        put("raw_iron", 17, "RAW_IRON");
        put("raw_copper", 17, "RAW_COPPER");
        put("raw_gold", 17, "RAW_GOLD");
        put("amethyst_shard", 17, "AMETHYST_SHARD");
        put("netherite_ingot", 16, "NETHERITE_INGOT");
        put("netherite_scrap", 16, "NETHERITE_SCRAP");
        put("ancient_debris", 16, "ANCIENT_DEBRIS");
        put("netherite_sword", 16, "NETHERITE_SWORD");
        put("netherite_pickaxe", 16, "NETHERITE_PICKAXE");
        put("netherite_axe", 16, "NETHERITE_AXE");
        put("netherite_shovel", 16, "NETHERITE_SHOVEL");
        put("netherite_hoe", 16, "NETHERITE_HOE");
        put("netherite_boots", 16, "NETHERITE_BOOTS");
        put("netherite_leggings", 16, "NETHERITE_LEGGINGS");
        put("netherite_chestplate", 16, "NETHERITE_CHESTPLATE");
        put("netherite_helmet", 16, "NETHERITE_HELMET");

        // Redstone & mechanics.
        put("redstone", 8, "REDSTONE");
        put("redstone_torch", 8, "REDSTONE_TORCH", "REDSTONE_TORCH_ON");
        put("piston", 8, "PISTON", "PISTON_BASE");
        put("hopper", 8, "HOPPER");
        put("comparator", 8, "COMPARATOR", "REDSTONE_COMPARATOR");
        put("repeater", 8, "REPEATER", "DIODE");
        put("redstone_lamp", 8, "REDSTONE_LAMP", "REDSTONE_LAMP_ON");
        put("lever", 8, "LEVER");
        put("stone_button", 8, "STONE_BUTTON");
        put("stone_pressure_plate", 8, "STONE_PRESSURE_PLATE", "STONE_PLATE");
        put("daylight_detector", 8, "DAYLIGHT_DETECTOR", "DAYLIGHT_DETECTOR");
        put("observer", 11, "OBSERVER");
        put("target", 16, "TARGET");
        put("sculk_sensor", 19, "SCULK_SENSOR");
        put("calibrated_sculk_sensor", 20, "CALIBRATED_SCULK_SENSOR");
        put("crafter", 21, "CRAFTER");
        put("copper_bulb", 21, "COPPER_BULB");

        // Decoration.
        put("flower_pot", 8, "FLOWER_POT", "FLOWER_POT_ITEM");
        put("item_frame", 8, "ITEM_FRAME");
        put("torch", 8, "TORCH");
        put("glowstone", 8, "GLOWSTONE");
        put("painting", 8, "PAINTING");
        put("jack_o_lantern", 8, "JACK_O_LANTERN");
        put("anvil", 8, "ANVIL");
        put("enchanting_table", 8, "ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        put("banner", 8, "WHITE_BANNER", "BANNER");
        put("lantern", 14, "LANTERN");
        put("soul_lantern", 16, "SOUL_LANTERN");
        put("candle", 17, "CANDLE");
        put("glow_item_frame", 17, "GLOW_ITEM_FRAME");
        put("amethyst_block", 17, "AMETHYST_BLOCK");
        put("concrete", 12, "WHITE_CONCRETE", "CONCRETE");
        put("concrete_powder", 12, "WHITE_CONCRETE_POWDER", "CONCRETE_POWDER");

        // Transport.
        put("rail", 8, "RAIL", "RAILS");
        put("powered_rail", 8, "POWERED_RAIL");
        put("detector_rail", 8, "DETECTOR_RAIL");
        put("activator_rail", 8, "ACTIVATOR_RAIL");
        put("minecart", 8, "MINECART");
        put("chest_minecart", 8, "CHEST_MINECART");
        put("furnace_minecart", 8, "FURNACE_MINECART");
        put("tnt_minecart", 8, "TNT_MINECART");
        put("saddle", 8, "SADDLE");
        put("boat", 8, "OAK_BOAT", "BOAT");
        put("spruce_boat", 8, "SPRUCE_BOAT", "BOAT");
        put("birch_boat", 8, "BIRCH_BOAT", "BOAT");
        put("mangrove_boat", 19, "MANGROVE_BOAT");
        put("cherry_boat", 20, "CHERRY_BOAT");

        // Nature.
        put("sapling", 8, "OAK_SAPLING", "SAPLING");
        put("oak_sapling", 8, "OAK_SAPLING", "SAPLING");
        put("spruce_sapling", 8, "SPRUCE_SAPLING", "SAPLING");
        put("birch_sapling", 8, "BIRCH_SAPLING", "SAPLING");
        put("jungle_sapling", 8, "JUNGLE_SAPLING", "SAPLING");
        put("acacia_sapling", 8, "ACACIA_SAPLING", "SAPLING");
        put("dark_oak_sapling", 8, "DARK_OAK_SAPLING", "SAPLING");
        put("cherry_sapling", 20, "CHERRY_SAPLING");
        put("mangrove_propagule", 19, "MANGROVE_PROPAGULE");
        put("wheat_seeds", 8, "WHEAT_SEEDS", "SEEDS");
        put("bone_meal", 8, "BONE_MEAL", "INK_SACK");
        put("dirt", 8, "DIRT");
        put("grass_block", 8, "GRASS_BLOCK", "GRASS");
        put("podzol", 8, "PODZOL");
        put("sugar_cane", 8, "SUGAR_CANE", "SUGAR_CANE_BLOCK");
        put("cactus", 8, "CACTUS");
        put("vine", 8, "VINE");
        put("lily_pad", 8, "LILY_PAD", "WATER_LILY");
        put("sunflower", 8, "SUNFLOWER", "DOUBLE_PLANT");
        put("pumpkin", 8, "PUMPKIN");
        put("dried_kelp", 13, "DRIED_KELP");
        put("sea_pickle", 13, "SEA_PICKLE");
        put("kelp", 13, "KELP");
        put("blue_ice", 13, "BLUE_ICE");

        // Brewing & alchemy.
        put("glass_bottle", 8, "GLASS_BOTTLE");
        put("brewing_stand", 8, "BREWING_STAND", "BREWING_STAND_ITEM");
        put("cauldron", 8, "CAULDRON", "CAULDRON_ITEM");
        put("blaze_powder", 8, "BLAZE_POWDER");
        put("fermented_spider_eye", 8, "FERMENTED_SPIDER_EYE");
        put("nether_wart", 8, "NETHER_WART", "NETHER_STALK");
        put("magma_cream", 8, "MAGMA_CREAM");
        put("glistering_melon", 8, "GLISTERING_MELON_SLICE", "SPECKLED_MELON");
        put("ghast_tear", 8, "GHAST_TEAR");
        put("dragon_breath", 9, "DRAGON_BREATH");
        put("splash_potion", 8, "SPLASH_POTION");
        put("lingering_potion", 9, "LINGERING_POTION");
        put("honey_bottle", 15, "HONEY_BOTTLE");
        put("honeycomb", 15, "HONEYCOMB");

        // Enchanted / special.
        put("chest", 8, "CHEST");
        put("experience_bottle", 8, "EXPERIENCE_BOTTLE", "EXP_BOTTLE");
        put("ender_pearl", 8, "ENDER_PEARL");
        put("ender_eye", 8, "ENDER_EYE", "EYE_OF_ENDER");
        put("name_tag", 8, "NAME_TAG");
        put("book", 8, "BOOK");
        put("enchanted_book", 8, "ENCHANTED_BOOK");
        put("paper", 8, "PAPER");
        put("string", 8, "STRING");
        put("slime_ball", 8, "SLIME_BALL");
        put("bone", 8, "BONE");
        put("gunpowder", 8, "GUNPOWDER", "SULPHUR");
        put("leather", 8, "LEATHER");
        put("nether_star", 8, "NETHER_STAR");
        put("beacon", 8, "BEACON");
        put("firework_rocket", 8, "FIREWORK_ROCKET", "FIREWORK");
        put("end_crystal", 9, "END_CRYSTAL");
        put("trident", 13, "TRIDENT");
        put("crossbow", 14, "CROSSBOW");
        put("spyglass", 17, "SPYGLASS");
        put("brush", 20, "BRUSH");
        put("breeze_rod", 21, "BREEZE_ROD");
        put("mace", 21, "MACE");
        put("heavy_core", 21, "HEAVY_CORE");
        put("trial_key", 21, "TRIAL_KEY");
        put("ominous_trial_key", 21, "OMINOUS_TRIAL_KEY");
        put("recovery_compass", 19, "RECOVERY_COMPASS");
        put("echo_shard", 19, "ECHO_SHARD");
        put("goat_horn", 19, "GOAT_HORN");

        // --- Color families: every dyeable block, so the shop is big on every era. ---
        colorFamily("wool", 8, "WOOL", "WOOL");
        colorFamily("carpet", 8, "CARPET", "CARPET");
        colorFamily("terracotta", 8, "TERRACOTTA", "STAINED_CLAY");
        colorFamily("concrete", 12, "CONCRETE", "CONCRETE");
        colorFamily("concrete_powder", 12, "CONCRETE_POWDER", "CONCRETE_POWDER");
        colorFamily("stained_glass", 8, "STAINED_GLASS", "STAINED_GLASS");
        colorFamily("glass_pane", 8, "STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        colorFamily("bed", 8, "BED", "BED");
        colorFamily("shulker_box", 11, "SHULKER_BOX", "SHULKER_BOX");

        // --- Wood families: stairs through trapdoors for every tree. ---
        woodFamily("oak", 8, 0);
        woodFamily("spruce", 8, 1);
        woodFamily("birch", 8, 2);
        woodFamily("jungle", 8, 3);
        woodFamily("acacia", 8, 4);
        woodFamily("dark_oak", 8, 5);
        woodFamily("mangrove", 19, 0);
        woodFamily("cherry", 20, 0);
        woodFamily("bamboo", 20, 0);

        // --- More masonry, stone and nether blocks. ---
        put("stone_bricks", 8, "STONE_BRICKS", "SMOOTH_BRICK");
        put("stone_brick_stairs", 8, "STONE_BRICK_STAIRS", "SMOOTH_STAIRS");
        put("cobblestone_stairs", 8, "COBBLESTONE_STAIRS");
        put("stone_slab", 8, "STONE_SLAB", "STEP");
        put("polished_andesite", 8, "POLISHED_ANDESITE");
        put("polished_diorite", 8, "POLISHED_DIORITE");
        put("polished_granite", 8, "POLISHED_GRANITE");
        put("quartz_block", 8, "QUARTZ_BLOCK");
        put("quartz_stairs", 8, "QUARTZ_STAIRS");
        put("smooth_quartz", 13, "SMOOTH_QUARTZ");
        put("purpur_block", 9, "PURPUR_BLOCK");
        put("purpur_stairs", 9, "PURPUR_STAIRS");
        put("purpur_pillar", 9, "PURPUR_PILLAR");
        put("end_stone_bricks", 9, "END_STONE_BRICKS");
        put("crimson_planks", 16, "CRIMSON_PLANKS");
        put("warped_planks", 16, "WARPED_PLANKS");
        put("crimson_stem", 16, "CRIMSON_STEM");
        put("warped_stem", 16, "WARPED_STEM");
        put("crimson_fungus", 16, "CRIMSON_FUNGUS");
        put("warped_fungus", 16, "WARPED_FUNGUS");
        put("nether_wart_block", 16, "NETHER_WART_BLOCK");
        put("warped_wart_block", 16, "WARPED_WART_BLOCK");
        put("shroomlight", 16, "SHROOMLIGHT");
        put("basalt", 16, "BASALT");
        put("blackstone", 16, "BLACKSTONE");
        put("polished_blackstone", 16, "POLISHED_BLACKSTONE");
        put("crying_obsidian", 16, "CRYING_OBSIDIAN");
        put("respawn_anchor", 16, "RESPAWN_ANCHOR");
        put("lodestone", 16, "LODESTONE");
        put("soul_sand", 8, "SOUL_SAND");
        put("soul_soil", 16, "SOUL_SOIL");
        put("deepslate", 17, "DEEPSLATE");
        put("cobbled_deepslate", 17, "COBBLED_DEEPSLATE");
        put("deepslate_bricks", 17, "DEEPSLATE_BRICKS");
        put("deepslate_tiles", 17, "DEEPSLATE_TILES");
        put("calcite", 17, "CALCITE");
        put("tuff", 17, "TUFF");
        put("budding_amethyst", 17, "BUDDING_AMETHYST");
        put("tinted_glass", 17, "TINTED_GLASS");
        put("mud", 19, "MUD");
        put("packed_mud", 19, "PACKED_MUD");
        put("mud_bricks", 19, "MUD_BRICKS");
        put("mangrove_log", 19, "MANGROVE_LOG");
        put("mangrove_planks", 19, "MANGROVE_PLANKS");
        put("mangrove_roots", 19, "MANGROVE_ROOTS");
        put("cherry_log", 20, "CHERRY_LOG");
        put("cherry_planks", 20, "CHERRY_PLANKS");
        put("bamboo_block", 20, "BAMBOO_BLOCK");
        put("bamboo_planks", 20, "BAMBOO_PLANKS");
        put("bamboo_mosaic", 20, "BAMBOO_MOSAIC");
        put("moss_block", 19, "MOSS_BLOCK");
        put("moss_carpet", 19, "MOSS_CARPET");
        put("azalea", 19, "AZALEA");
        put("flowering_azalea", 19, "FLOWERING_AZALEA");
        put("big_dripleaf", 19, "BIG_DRIPLEAF");
        put("spore_blossom", 19, "SPORE_BLOSSOM");
        put("glow_lichen", 19, "GLOW_LICHEN");
        put("lightning_rod", 17, "LIGHTNING_ROD");

        // --- Vanilla heads: real decorative skulls for every era. ---
        putD("skeleton_skull", 8, 0, "SKELETON_SKULL", "SKULL_ITEM");
        putD("wither_skeleton_skull", 9, 1, "WITHER_SKELETON_SKULL", "SKULL_ITEM");
        putD("zombie_head", 8, 2, "ZOMBIE_HEAD", "SKULL_ITEM");
        putD("creeper_head", 8, 3, "CREEPER_HEAD", "SKULL_ITEM");
        putD("dragon_head", 9, 5, "DRAGON_HEAD", "SKULL_ITEM");
        put("piglin_head", 16, "PIGLIN_HEAD");

        // --- Utility, storage and redstone blocks. ---
        put("redstone_block", 8, "REDSTONE_BLOCK");
        put("chain", 16, "IRON_CHAIN", "CHAIN");
        put("bell", 14, "BELL");
        put("lectern", 14, "LECTERN");
        put("jukebox", 8, "JUKEBOX");
        put("note_block", 8, "NOTE_BLOCK");
        put("dispenser", 8, "DISPENSER");
        put("dropper", 8, "DROPPER");
        put("trapped_chest", 8, "TRAPPED_CHEST");
        put("blast_furnace", 14, "BLAST_FURNACE");
        put("smoker", 14, "SMOKER");
        put("stonecutter", 14, "STONECUTTER");
        put("campfire", 14, "CAMPFIRE");
        put("soul_campfire", 16, "SOUL_CAMPFIRE");
        put("scaffolding", 14, "SCAFFOLDING");
        put("slime_block", 8, "SLIME_BLOCK");
        put("honey_block", 15, "HONEY_BLOCK");
        put("tnt", 8, "TNT");

        // --- Compact metal blocks. ---
        put("iron_block", 8, "IRON_BLOCK");
        put("gold_block", 8, "GOLD_BLOCK");
        put("diamond_block", 8, "DIAMOND_BLOCK");
        put("emerald_block", 8, "EMERALD_BLOCK");
        put("lapis_block", 8, "LAPIS_BLOCK");
        put("coal_block", 8, "COAL_BLOCK");
        put("netherite_block", 16, "NETHERITE_BLOCK");
        put("raw_iron_block", 17, "RAW_IRON_BLOCK");
        put("raw_copper_block", 17, "RAW_COPPER_BLOCK");
        put("raw_gold_block", 17, "RAW_GOLD_BLOCK");

        // --- More items: tools, drops, food. ---
        put("stick", 8, "STICK");
        put("flint", 8, "FLINT");
        put("feather", 8, "FEATHER");
        put("rotten_flesh", 8, "ROTTEN_FLESH");
        put("spider_eye", 8, "SPIDER_EYE");
        put("sugar", 8, "SUGAR");
        put("glowstone_dust", 8, "GLOWSTONE_DUST");
        put("blaze_rod", 8, "BLAZE_ROD");
        put("shulker_shell", 11, "SHULKER_SHELL");
        put("phantom_membrane", 13, "PHANTOM_MEMBRANE");
        put("prismarine_shard", 8, "PRISMARINE_SHARD");
        put("prismarine_crystals", 8, "PRISMARINE_CRYSTALS");
        put("nautilus_shell", 13, "NAUTILUS_SHELL");
        put("heart_of_the_sea", 13, "HEART_OF_THE_SEA");
        put("conduit", 13, "CONDUIT");
        put("turtle_scute", 13, "SCUTE");
        put("chorus_fruit", 9, "CHORUS_FRUIT");
        put("popped_chorus_fruit", 9, "POPPED_CHORUS_FRUIT");
        put("mushroom_stew", 8, "MUSHROOM_STEW", "MUSHROOM_SOUP");
        put("rabbit_stew", 8, "RABBIT_STEW");
        put("beetroot", 8, "BEETROOT");
        put("beetroot_soup", 8, "BEETROOT_SOUP", "BEET_ROOT_SOUP");
        put("suspicious_stew", 14, "SUSPICIOUS_STEW");
        put("pufferfish", 8, "PUFFERFISH");
        put("tropical_fish", 8, "TROPICAL_FISH");
        put("salmon", 8, "SALMON", "RAW_SALMON");
        put("cod", 8, "COD", "RAW_FISH");
        put("milk_bucket", 8, "MILK_BUCKET");

        // --- Flowers: cheap pretties for nature and decoration. ---
        put("dandelion", 8, "DANDELION", "YELLOW_FLOWER");
        put("poppy", 8, "POPPY", "RED_ROSE");
        put("blue_orchid", 8, "BLUE_ORCHID", "RED_ROSE");
        put("allium", 8, "ALLIUM", "RED_ROSE");
        put("azure_bluet", 8, "AZURE_BLUET", "RED_ROSE");
        put("red_tulip", 8, "RED_TULIP", "RED_ROSE");
        put("orange_tulip", 8, "ORANGE_TULIP", "RED_ROSE");
        put("white_tulip", 8, "WHITE_TULIP", "RED_ROSE");
        put("pink_tulip", 8, "PINK_TULIP", "RED_ROSE");
        put("oxeye_daisy", 8, "OXEYE_DAISY", "RED_ROSE");
        put("cornflower", 12, "CORNFLOWER");
        put("lily_of_the_valley", 14, "LILY_OF_THE_VALLEY");
        put("wither_rose", 14, "WITHER_ROSE");
        put("torchflower", 20, "TORCHFLOWER");
        put("rose_bush", 8, "ROSE_BUSH", "DOUBLE_PLANT");
        put("lilac", 8, "LILAC", "DOUBLE_PLANT");
        put("peony", 8, "PEONY", "DOUBLE_PLANT");

        // --- Sculk, the 1.19 nature set. ---
        put("sculk", 19, "SCULK");
        put("sculk_catalyst", 19, "SCULK_CATALYST");
        put("sculk_shrieker", 19, "SCULK_SHRIEKER");
        put("sculk_vein", 19, "SCULK_VEIN");
    }

    /**
     * A fully or partially resolved registry row. For a runtime query on the
     * running server, {@code material()} is the actual enum value and
     * {@code data()} the legacy durability to attach so the variant stays
     * distinct (red wool is red, a creeper head is a creeper). For a pure
     * per-version knowledge query there is no {@code material()} - callers get
     * the name a given 1.x era would use instead.
     */
    public static final class Resolved {

        private final String key;
        private final int since;
        private final String name;
        private final Material material;
        private final short data;
        private final boolean available;

        Resolved(String key, int since, String name, Material material, short data, boolean available) {
            this.key = key;
            this.since = since;
            this.name = name;
            this.material = material;
            this.data = data;
            this.available = available;
        }

        public String key() {
            return key;
        }

        /** The earliest 1.x minor that introduced this key. */
        public int since() {
            return since;
        }

        /** The enum constant used (runtime) or the canonical name for the queried era. */
        public String name() {
            return name;
        }

        /** Non-null only for runtime resolution on this server. */
        public Material material() {
            return material;
        }

        /** Legacy durability the material needs to keep its variant on a pre-1.13-style set. */
        public short data() {
            return data;
        }

        /** True when the queried version can carry this item. */
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String toString() {
            return "Resolved[" + key + " since=" + since + " available=" + available
                    + " name=" + name + " material=" + material + " data=" + data + "]";
        }
    }

    /** The 1.13 flattening: from here every variant is its own enum constant. */
    private static final int FLATTEN = 13;

    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<String, Resolved>();

    private final int serverMinor;

    public MaterialLookup(ServerVersion version) {
        this.serverMinor = version.getMinor();
    }

    /** A resolved item for the key the reader currently renders (never on the hook). */
    public Material get(String key) {
        return material(key);
    }

    /**
     * The material for {@code key} on this server. Returns {@code null} when the
     * key is unknown, newer than the server, or has no material on this version.
     */
    public Material material(String key) {
        Resolved resolved = item(key);
        return resolved == null ? null : resolved.material();
    }

    /**
     * The full runtime resolution of {@code key} on this server: the enum value
     * and the legacy durability it needs to stay distinct from its family's base
     * variant. Returns null when the key cannot be carried on this server at all.
     */
    public Resolved item(String key) {
        return resolveItem(key, serverMinor);
    }

    /**
     * Version-gated resolution for a specific client: {@code clientMinor} is the
     * 1.x minor of the client (from {@code ViaVersionHook}, 0 when unknown - then
     * this degrades to the server's own resolution).
     */
    public Material materialForClient(String key, int clientMinor) {
        Resolved resolved = resolveItem(key, clientMinor <= 0 ? serverMinor : Math.min(clientMinor, serverMinor));
        return resolved == null ? null : resolved.material();
    }

    /** Whether the running server can represent this key. */
    public boolean available(String key) {
        return item(key) != null;
    }

    /** Whether the given 1.x minor can carry this key, by registry knowledge alone. */
    public boolean availableOn(String key, int minor) {
        Entry entry = ENTRIES.get(key);
        return entry != null && minor >= entry.since;
    }

    /** Knowledge of this key as the queried 1.x minor would see it, with no live server involved. */
    public Resolved known(String key, int minor) {
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            return null;
        }
        String name = entry.names[0];
        short data = 0;
        if (minor < FLATTEN && entry.names.length > 1) {
            name = entry.names[entry.names.length - 1];
            data = entry.dataAt(entry.names.length - 1);
        }
        boolean available = minor >= entry.since;
        return new Resolved(key, entry.since, available ? name : null, null, available ? data : (short) 0, available);
    }

    /** Every registry key the queried 1.x minor can carry, in declaration order. */
    public List<String> keysFor(int minor) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Entry> row : ENTRIES.entrySet()) {
            if (minor >= row.getValue().since) {
                out.add(row.getKey());
            }
        }
        return out;
    }

    /** The earliest 1.x release that introduced this key, or 1 when unknown. */
    public static int since(String key) {
        Entry entry = ENTRIES.get(key);
        return entry == null ? 1 : entry.since;
    }

    private static final String[] PROBE = {
            "STONE", "COBBLESTONE", "OAK_PLANKS", "SPRUCE_PLANKS", "RED_WOOL", "WHITE_WOOL",
            "RED_BED", "RED_STAINED_GLASS", "RED_TERRACOTTA", "TERRACOTTA", "IRON_CHAIN",
            "WOODEN_SWORD", "DIAMOND_SHOVEL", "END_STONE", "BLUE_ICE", "CROSSBOW", "TRIDENT",
            "SPYGLASS", "CARROT_ON_A_STICK", "COPPER_BLOCK"
    };

    /**
     * One-line diagnostic of what this server's Material enum actually carries.
     * Different Paper builds around the ItemType/BlockType release dropped whole
     * swathes of constants for a couple of builds, so this shows at a glance
     * whether the modern variants exist on the running jar (they do exist on the
     * current stable 26.2 build). Logged once at startup.
     */
    public static String probe() {
        int present = 0;
        StringBuilder missing = new StringBuilder();
        for (String name : PROBE) {
            if (Material.getMaterial(name) != null) {
                present++;
            } else {
                missing.append(name).append(' ');
            }
        }
        return present + "/" + PROBE.length + " modern constants present"
                + (missing.length() == 0 ? "" : "; missing: " + missing.toString().trim());
    }

    /** All registry keys, in declaration order. */
    public Collection<String> keys() {
        return ENTRIES.keySet();
    }

    private Resolved resolveItem(String key, int minor) {
        String cacheKey = key + "#" + minor;
        Resolved cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Entry entry = ENTRIES.get(key);
        if (entry == null || minor < entry.since) {
            return null;
        }
        // ConcurrentHashMap forbids null values, so unresolvable keys are simply
        // not cached - the resolution below is cheap and re-runs every call.
        //
        // On 1.13+ only the modern name may be used. The pre-rename names, when
        // present on a modern enum at all, are deprecated aliases for the base
        // variant: letting red wool fall through to plain "WOOL" is what turned
        // every colour into white wool and every head into a skeleton skull, so
        // a modern-era key whose modern name is missing stays unresolved instead
        // of silently collapsing into its family's base material.
        boolean modernEra = minor >= FLATTEN;
        Material material = null;
        short data = 0;
        String used = null;
        for (int i = 0; i < entry.names.length; i++) {
            if (modernEra && i > 0) {
                break;
            }
            material = byName(entry.names[i]);
            if (material != null) {
                used = entry.names[i];
                data = entry.dataAt(i);
                break;
            }
        }
        // Last resort: the key itself is almost always a valid lowercase
        // resource name (e.g. "painting", "minecart", "brewing_stand"), and
        // matchMaterial resolves it on every 1.13+ server. This guarantees a
        // category icon never degrades to a bare chest.
        if (material == null) {
            material = byName(key);
            if (material != null) {
                used = key;
                data = 0;
            }
        }
        if (material == null) {
            return null;
        }
        Resolved resolved = new Resolved(key, entry.since, used, material, data, true);
        CACHE.put(cacheKey, resolved);
        return resolved;
    }

    private static Material byName(String name) {
        // valueOf hits the enum constant directly; getMaterial does extra legacy
        // processing that misses some modern names on 1.13 (e.g. TOTEM_OF_UNDYING).
        // Try the direct constant first, then the lenient lookups, then the
        // lower-case registry spelling (what getMaterial accepts on 1.13+).
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            try {
                Material material = Material.getMaterial(name);
                if (material != null) {
                    return material;
                }
                material = Material.matchMaterial(name);
                if (material != null) {
                    return material;
                }
                return Material.getMaterial(name.toLowerCase(Locale.ROOT));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}