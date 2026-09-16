package dev.minted.compat;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a stable key (e.g. {@code "elytra"}, {@code "diamond_sword"}) to the
 * right {@link Material} on the running server.
 *
 * <p>The material enum was renamed at 1.13, and many items simply do not exist
 * before the version that introduced them. Each key holds candidate enum names,
 * modern spelling first; the first that {@link Material#getMaterial} resolves
 * wins, and a key nothing resolves for returns {@code null} - callers then hide
 * the entry rather than crash. Compiles against 1.8 because everything is by
 * name; no modern constant is referenced directly.
 */
public final class MaterialLookup {

    // key -> candidate enum names, modern first then legacy. Only keys the shop uses.
    private static final Map<String, String[]> KEYS = new LinkedHashMap<String, String[]>();

    private static void put(String key, String... names) {
        KEYS.put(key, names);
    }

    static {
        // Furniture / category icons.
        put("chest", "CHEST");
        put("bricks", "BRICKS", "BRICK");
        put("diamond_sword", "DIAMOND_SWORD");
        put("iron_pickaxe", "IRON_PICKAXE");
        put("iron_chestplate", "IRON_CHESTPLATE");
        put("cooked_beef", "COOKED_BEEF", "GRILLED_PORK");
        put("bread", "BREAD");
        put("iron_ore", "IRON_ORE");
        put("redstone", "REDSTONE");
        put("painting", "PAINTING");
        put("minecart", "MINECART");
        put("sapling", "OAK_SAPLING", "SAPLING");
        put("brewing_stand", "BREWING_STAND", "BREWING_STAND_ITEM");
        put("enchanted_book", "ENCHANTED_BOOK");
        put("nether_star", "NETHER_STAR");

        // Building.
        put("stone", "STONE");
        put("cobblestone", "COBBLESTONE");
        put("oak_planks", "OAK_PLANKS", "WOOD");
        put("glass", "GLASS");
        put("oak_log", "OAK_LOG", "LOG");
        put("sandstone", "SANDSTONE");

        // Tools & weapons.
        put("wooden_sword", "WOODEN_SWORD", "WOOD_SWORD");
        put("stone_axe", "STONE_AXE");
        put("bow", "BOW");
        put("fishing_rod", "FISHING_ROD");
        put("shield", "SHIELD");

        // Armor & combat.
        put("leather_chestplate", "LEATHER_CHESTPLATE");
        put("iron_helmet", "IRON_HELMET");
        put("diamond_chestplate", "DIAMOND_CHESTPLATE");
        put("shulker_box", "SHULKER_BOX", "PURPLE_SHULKER_BOX");
        put("totem", "TOTEM_OF_UNDYING");
        put("elytra", "ELYTRA");

        // Food.
        put("apple", "APPLE");
        put("golden_apple", "GOLDEN_APPLE");
        put("cooked_chicken", "COOKED_CHICKEN");
        put("cake", "CAKE");
        put("carrot", "CARROT", "CARROT_ITEM");

        // Ores & materials.
        put("coal", "COAL");
        put("iron_ingot", "IRON_INGOT");
        put("gold_ingot", "GOLD_INGOT");
        put("diamond", "DIAMOND");
        put("emerald", "EMERALD");
        put("lapis", "LAPIS_LAZULI", "INK_SACK");

        // Redstone & mechanics.
        put("redstone_torch", "REDSTONE_TORCH", "REDSTONE_TORCH_ON");
        put("piston", "PISTON", "PISTON_BASE");
        put("hopper", "HOPPER");
        put("comparator", "COMPARATOR", "REDSTONE_COMPARATOR");
        put("observer", "OBSERVER");

        // Decoration.
        put("flower_pot", "FLOWER_POT", "FLOWER_POT_ITEM");
        put("item_frame", "ITEM_FRAME");
        put("torch", "TORCH");
        put("glowstone", "GLOWSTONE");

        // Transport.
        put("rail", "RAIL", "RAILS");
        put("powered_rail", "POWERED_RAIL");
        put("boat", "OAK_BOAT", "BOAT");
        put("saddle", "SADDLE");

        // Nature.
        put("oak_sapling", "OAK_SAPLING", "SAPLING");
        put("wheat_seeds", "WHEAT_SEEDS", "SEEDS");
        put("bone_meal", "BONE_MEAL", "INK_SACK");
        put("dirt", "DIRT");

        // Brewing & alchemy.
        put("glass_bottle", "GLASS_BOTTLE");
        put("blaze_powder", "BLAZE_POWDER");
        put("fermented_spider_eye", "FERMENTED_SPIDER_EYE");
        put("nether_wart", "NETHER_WART", "NETHER_STALK");

        // Enchanted / special.
        put("experience_bottle", "EXPERIENCE_BOTTLE", "EXP_BOTTLE");
        put("ender_pearl", "ENDER_PEARL");
        put("name_tag", "NAME_TAG");
        put("book", "BOOK");
    }

    private final Map<String, Material> cache = new HashMap<String, Material>();

    public MaterialLookup(ServerVersion version) {
        // Version kept for parity with the other compat types; resolution is by
        // name so it is inherently version-correct without branching on it.
    }

    /** The material for {@code key} on this server, or {@code null} if none exists here. */
    public Material get(String key) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Material resolved = resolve(key);
        cache.put(key, resolved);
        return resolved;
    }

    private static Material resolve(String key) {
        String[] names = KEYS.get(key);
        if (names == null) {
            return null;
        }
        for (String name : names) {
            Material material = byName(name);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private static Material byName(String name) {
        // valueOf hits the enum constant directly; getMaterial does extra legacy
        // processing that misses some modern names on 1.13 (e.g. TOTEM_OF_UNDYING).
        // Try the direct constant first, then the lenient lookups.
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            Material material = Material.getMaterial(name);
            return material != null ? material : Material.matchMaterial(name);
        }
    }
}
