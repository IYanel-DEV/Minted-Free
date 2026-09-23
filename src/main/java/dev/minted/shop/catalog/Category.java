package dev.minted.shop.catalog;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The canonical, code-owned category list - the single source both the global
 * catalog and community listings pick from. Each has a stable {@link #key}
 * (stored on rows), a display name and a version-safe {@link #iconKey} resolved
 * through {@code MaterialLookup}. Display names may be overridden in config; the
 * set itself is fixed. {@link #guess} picks a sensible default by material so a
 * player listing an item does not start from nothing.
 */
public enum Category {

    BUILDING("building", "Building", "bricks", Material.STONE),
    TOOLS("tools", "Tools & Weapons", "diamond_sword", Material.IRON_SWORD),
    ARMOR("armor", "Armor & Combat", "iron_chestplate", Material.IRON_CHESTPLATE),
    FOOD("food", "Food", "bread", Material.BREAD),
    ORES("ores", "Ores & Materials", "iron_ore", Material.IRON_ORE),
    REDSTONE("redstone", "Redstone & Mechanics", "redstone", Material.REDSTONE),
    DECORATION("decoration", "Decoration", "painting", Material.PAINTING),
    TRANSPORT("transport", "Transport", "minecart", Material.MINECART),
    NATURE("nature", "Nature", "sapling", Material.CHEST),
    BREWING("brewing", "Brewing & Alchemy", "brewing_stand", Material.BREWING_STAND),
    ENCHANTED("enchanted", "Enchanted", "enchanted_book", Material.ENCHANTED_BOOK),
    MISC("misc", "Misc", "chest", Material.CHEST);

    private final String key;
    private final String display;
    private final String iconKey;
    /**
     * A material constant that exists on every supported release (1.8 and
     * 1.13+) , used when the version-aware icon key cannot resolve so a menu
     * tile always shows a real item - never a placeholder.
     */
    private final Material guaranteed;

    Category(String key, String display, String iconKey, Material guaranteed) {
        this.key = key;
        this.display = display;
        this.iconKey = iconKey;
        this.guaranteed = guaranteed;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public String iconKey() {
        return iconKey;
    }

    /** A material that exists on every supported server, for icon fallbacks. */
    public Material guaranteed() {
        return guaranteed;
    }

    /** The category with this key, or {@link #MISC} for null/unknown (old rows). */
    public static Category byKey(String key) {
        if (key != null) {
            for (Category category : values()) {
                if (category.key.equalsIgnoreCase(key)) {
                    return category;
                }
            }
        }
        return MISC;
    }

    /** A best-guess category for a material, used as the listing default. */
    public static Category guess(Material material) {
        // ponytail: substring heuristic over the enum name; good enough for a
        // default the player can override, not a taxonomy.
        String n = material.name().toUpperCase(Locale.ROOT);
        if (has(n, "SWORD", "PICKAXE", "AXE", "SHOVEL", "SPADE", "HOE", "BOW", "ROD", "SHEARS", "FLINT_AND")) {
            return TOOLS;
        }
        if (has(n, "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SHIELD")) {
            return ARMOR;
        }
        if (has(n, "APPLE", "BREAD", "BEEF", "PORK", "CHICKEN", "MUTTON", "RABBIT", "COD", "SALMON",
                "FISH", "CARROT", "POTATO", "CAKE", "COOKIE", "MELON", "STEW", "SOUP", "BERRIES")) {
            return FOOD;
        }
        if (has(n, "ORE", "INGOT", "DIAMOND", "EMERALD", "COAL", "LAPIS", "QUARTZ", "NUGGET", "SCRAP", "NETHERITE")) {
            return ORES;
        }
        if (has(n, "REDSTONE", "PISTON", "HOPPER", "COMPARATOR", "REPEATER", "OBSERVER",
                "DISPENSER", "DROPPER", "LEVER", "BUTTON", "PRESSURE")) {
            return REDSTONE;
        }
        if (has(n, "RAIL", "MINECART", "BOAT", "SADDLE", "ELYTRA")) {
            return TRANSPORT;
        }
        if (has(n, "POTION", "BLAZE", "WART", "SPIDER_EYE", "GLASS_BOTTLE", "GLISTERING", "BREWING")) {
            return BREWING;
        }
        if (has(n, "ENCHANT", "EXP", "EXPERIENCE", "ENDER_PEARL", "TOTEM", "NETHER_STAR", "BOOK")) {
            return ENCHANTED;
        }
        if (has(n, "PAINTING", "FRAME", "TORCH", "GLOWSTONE", "BANNER", "CARPET", "FLOWER_POT", "SKULL", "HEAD")) {
            return DECORATION;
        }
        if (has(n, "SAPLING", "SEED", "FLOWER", "LEAVES", "GRASS", "WHEAT", "SUGAR", "BONE_MEAL",
                "DIRT", "GRAVEL", "MUSHROOM", "VINE", "KELP")) {
            return NATURE;
        }
        if (has(n, "LOG", "PLANK", "STONE", "BRICK", "COBBLE", "SAND", "GLASS", "WOOL",
                "CONCRETE", "TERRACOTTA", "WOOD", "PRISMARINE", "DEEPSLATE")) {
            return BUILDING;
        }
        return MISC;
    }

    private static boolean has(String name, String... needles) {
        for (String needle : needles) {
            if (name.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
