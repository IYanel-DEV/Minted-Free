package dev.minted.shop.storage;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.shop.Currency;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopItem;
import dev.minted.shop.ShopType;
import dev.minted.shop.catalog.Catalog;
import dev.minted.shop.catalog.Category;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The disk home of every admin-managed global shop: {@code global-shops.yml} in
 * the plugin folder. Items are stored as version-safe data - a canonical
 * {@link MaterialLookup} key, an optional legacy data value for pre-1.13
 * variants, and a plain-text subset of the cosmetics (name, lore, enchants)
 * - so the same file stocks a 1.8 server and a 1.26 server, and a shop website
 * can generate and read it directly. Community and player shops never appear
 * here; they keep their real stock and ownership in the database.
 *
 * <p>One shop may carry {@code catalog: true}, which makes the loader append any
 * preset catalog item the running version supports but the shop does not yet
 * sell (the old "grow every global shop" behaviour, now opt-in and never fired
 * for hand-written files). {@link #grow} is what performs that append while the
 * file stays exactly as written in every other case.
 */
public final class GlobalShopFile {

    public static final String FILE_NAME = "global-shops.yml";

    private static final String HEADER =
            "# Minted global-shop catalog (" + FILE_NAME + ")\n"
                    + "#\n"
                    + "# Every ADMIN-managed global shop lives here; community and player\n"
                    + "# shops stay in the database. Items use version-safe material keys (plus\n"
                    + "# an optional legacy data value), so one file stocks a 1.8 and a 1.26\n"
                    + "# server alike. Minted rewrites this file whenever a global shop changes\n"
                    + "# in game, so the in-game editor and the file always agree - and it is\n"
                    + "# the file a shop website would generate or download.\n"
                    + "#\n"
                    + "# Fields a shop can carry: name, catalog (true = append new preset items\n"
                    + "# on load), currency (wallet|bank), icon {material, data, name}.\n"
                    + "# Fields an item can carry: material, data, amount, name, lore, enchants\n"
                    + "# (enchant-name: level), buy, sell, category, page, slot. An omitted buy\n"
                    + "# or sell price closes that side of the trade; page/slot may be omitted,\n"
                    + "# in which case items are laid out in the order they appear.\n";

    /** The 1.13 flattening: from here every variant is its own enum constant. */
    private static final int FLATTEN = 13;

    private final File file;
    private final ServerVersion version;
    private final MaterialLookup materials;
    private final Logger log;

    // Shop names (lower-cased) marked catalog: true in the file - the only shops
    // growth may touch. Repopulated on every load and kept in step on rename.
    private final Set<String> growable = new HashSet<String>();

    public GlobalShopFile(File dataFolder, ServerVersion version, MaterialLookup materials, Logger log) {
        this.file = new File(dataFolder, FILE_NAME);
        this.version = version;
        this.materials = materials;
        this.log = log;
    }

    public String fileName() {
        return FILE_NAME;
    }

    public boolean exists() {
        return file.exists();
    }

    /** Writes the default catalog file for this server version (fresh install). */
    public void writeDefault() {
        save(defaultShops());
    }

    /**
     * Reads the file into in-memory shop objects. Ids are assigned from
     * {@code idBase} upwards so they never collide with the database shops.
     * Unrepresentable entries are skipped with one summary warning, never fatal.
     * Runs on the main thread, where item construction is safe.
     */
    public List<Shop> load(int idBase) {
        List<Shop> out = new ArrayList<Shop>();
        growable.clear();
        if (!file.exists()) {
            return out;
        }
        YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
        List<?> shopList = conf.getList("shops");
        if (shopList == null || shopList.isEmpty()) {
            return out;
        }
        int index = 0;
        int skipped = 0;
        for (Object raw : shopList) {
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<?, ?> shopMap = (Map<?, ?>) raw;
            String name = asString(shopMap, "name");
            if (name == null || name.trim().isEmpty()) {
                log.warning("Ignoring a " + FILE_NAME + " shop with no name.");
                continue;
            }
            int id = idBase + index++;
            Currency currency = Currency.fromId(asString(shopMap, "currency", "wallet"));
            ItemStack icon = readIcon(shopMap.get("icon"));
            if (icon == null) {
                icon = defaultIcon(name);
            }
            Shop shop = new Shop(id, name, icon, currency, ShopType.GLOBAL, null);
            if (asBool(shopMap, "catalog", false)) {
                growable.add(key(shop.getName()));
            }
            skipped += attachItems(shop, shopMap.get("items"));
            out.add(shop);
        }
        if (skipped > 0) {
            log.warning("Ignored " + skipped + " item(s) in " + FILE_NAME
                    + " that this server version cannot represent.");
        }
        return out;
    }

    /** Whether the file marks this shop for automatic catalog growth. */
    public boolean wantsGrowth(Shop shop) {
        return growable.contains(key(shop.getName()));
    }

    /**
     * Appends any preset catalog entry the running version supports that this
     * shop does not already sell, plus every remaining material the server can
     * carry, so a {@code catalog: true} shop ends up selling every single item
     * the version has. Idempotent: existing items - including admin price and
     * position edits - are untouched. Only called for shops whose file entry
     * says {@code catalog: true}.
     */
    public void grow(Shop shop) {
        Set<Material> present = new HashSet<Material>();
        for (ShopItem item : shop.allItems()) {
            present.add(item.copy().getType());
        }
        int added = 0;
        for (Catalog.Entry entry : Catalog.entriesFor(version)) {
            MaterialLookup.Resolved resolved = materials.item(entry.materialKey);
            if (resolved == null || !isItem(resolved.material()) || present.contains(resolved.material())) {
                continue;
            }
            int address = shop.firstFreeAddress();
            if (address < 0) {
                break;
            }
            ShopItem item = new ShopItem(shop.getId(), address / Shop.SLOTS_PER_PAGE,
                    address % Shop.SLOTS_PER_PAGE,
                    named(resolved.material(), resolved.data(), ChatColor.WHITE + entry.display),
                    entry.buy, entry.sell, entry.category.key());
            shop.put(item);
            present.add(resolved.material());
            added++;
        }
        int extra = fillEveryMaterial(shop, present);
        if (added + extra > 0) {
            log.info("Grew the global shop '" + shop.getName() + "' by " + (added + extra)
                    + " item(s) (" + added + " curated, " + extra + " every-material).");
        }
    }

    /**
     * Sells every remaining {@code Material} of the running server that the shop
     * has not covered yet - the "every single item" half of a catalog shop.
     * Curated rows win display name, category and price; anything the curated
     * catalog misses gets a prettified name, a {@link Category#guess} category
     * and that category's default price. Legacy data variants are a single enum
     * value, so a material added once (any color) counts as covered. Returns how
     * many rows were added, or 0 when the shop is full or already complete.
     */
    private int fillEveryMaterial(Shop shop, Set<Material> present) {
        int added = 0;
        for (Material material : sellableMaterials()) {
            if (!present.add(material)) {
                continue;
            }
            int address = shop.firstFreeAddress();
            if (address < 0) {
                break;
            }
            ItemStack stack = new ItemStack(material);
            short data = identityData(stack);
            String key = materials.keyFor(material, data);
            Catalog.Entry curated = key == null ? null : Catalog.find(key);
            Category category = curated == null ? Category.guess(material) : curated.category;
            double buy = curated != null ? curated.buy : Catalog.defaultPrice(category)[0];
            double sell = curated != null ? curated.sell : Catalog.defaultPrice(category)[1];
            String display = curated != null ? curated.display : prettify(material.name());
            ShopItem item = new ShopItem(shop.getId(), address / Shop.SLOTS_PER_PAGE,
                    address % Shop.SLOTS_PER_PAGE,
                    named(material, data, ChatColor.WHITE + display), buy, sell, category.key());
            shop.put(item);
            added++;
        }
        return added;
    }

    /**
     * Every material of the running server that is worth a shop row, in enum
     * order. Same filter the fill, the default seed and (via the public count)
     * the self-test use, so "every single item" is defined exactly once.
     */
    public static List<Material> sellableMaterials() {
        List<Material> out = new ArrayList<Material>();
        for (Material material : Material.values()) {
            if (isSellable(material)) {
                out.add(material);
            }
        }
        return out;
    }

    /**
     * Whether a material should be sold as a shop row. Excludes air, technical
     * block state placeholders (stationary/portal/fire machinery that merely
     * exists in the material enum) and server-only blocks like command blocks,
     * structures and light. {@link #isItem} filters users' items on 1.13+; the
     * denylist below covers the versions where it fails open.
     */
    private static boolean isSellable(Material material) {
        if (!isItem(material)) {
            return false;
        }
        String n = material.name();
        if (n.equals("AIR") || n.startsWith("CAVE_AIR") || n.startsWith("VOID_AIR")
                || n.startsWith("STATIONARY_") || n.equals("WATER") || n.equals("LAVA")
                || n.equals("LIGHT") || n.contains("PORTAL") || n.equals("END_GATEWAY")
                || n.equals("FIRE") || n.equals("BEDROCK") || n.equals("BARRIER")
                || n.equals("PISTON_EXTENSION") || n.equals("PISTON_MOVING_PIECE")
                || n.equals("MOB_SPAWNER") || n.equals("SPAWNER")
                || n.startsWith("COMMAND") || n.startsWith("STRUCTURE") || n.equals("JIGSAW")
                || n.equals("GLOWING_REDSTONE_ORE") || n.equals("BURNING_FURNACE")
                || n.equals("FROSTED_ICE") || n.equals("LIT_")
                || n.endsWith("_SPAWN_EGG") || n.equals("KNOWLEDGE_BOOK")
                || n.equals("DEBUG_STICK")) {
            return false;
        }
        return true;
    }

    /** "NETHERITE_SCRAP" -> "Netherite Scrap". */
    private static String prettify(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /**
     * Rewrites the whole file from the given file-backed shops, in order given.
     *
     * @return true only when the file was actually written; false when the folder
     *         or disk made it impossible (already logged).
     */
    public boolean save(Collection<Shop> fileShops) {
        List<Map<String, Object>> shops = new ArrayList<Map<String, Object>>();
        for (Shop shop : fileShops) {
            shops.add(fromShop(shop));
        }
        YamlConfiguration tree = new YamlConfiguration();
        try {
            tree.set("shops", shops);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), (HEADER + tree.saveToString()).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            log.severe("Could not write " + FILE_NAME + ": " + e.getMessage());
            return false;
        }
    }

    /** Keeps the growth marker attached to its shop across a rename. */
    public void remapGrowable(String fromLower, String toLower) {
        if (growable.remove(fromLower)) {
            growable.add(toLower);
        }
    }

    /** Forgets the growth marker of a deleted file shop. */
    public void drop(String nameLower) {
        growable.remove(key(nameLower));
    }

    // ------------------------------------------------------------------
    // Encoding a shop into the canonical map form.
    // ------------------------------------------------------------------

    private Map<String, Object> fromShop(Shop shop) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", shop.getName());
        if (growable.contains(key(shop.getName()))) {
            out.put("catalog", true);
        }
        out.put("currency", shop.getCurrency().id());
        Map<String, Object> icon = fromIcon(shop);
        if (icon != null) {
            out.put("icon", icon);
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ShopItem item : shop.allItems()) {
            items.add(fromItem(item));
        }
        out.put("items", items);
        return out;
    }

    private Map<String, Object> fromIcon(Shop shop) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        ItemStack icon = shop.getIcon();
        String key = materials.keyFor(icon.getType(), identityData(icon));
        if (key == null) {
            return null;
        }
        out.put("material", key);
        short data = identityData(icon);
        if (data != 0) {
            out.put("data", (int) data);
        }
        if (icon.hasItemMeta() && icon.getItemMeta().hasDisplayName()) {
            out.put("name", toAmpersand(icon.getItemMeta().getDisplayName()));
        }
        return out;
    }

    private Map<String, Object> fromItem(ShopItem item) {
        ItemStack stack = item.copy();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        String key = materials.keyFor(stack.getType(), identityData(stack));
        if (key != null) {
            out.put("material", key);
        } else {
            // An item the registry does not know (a newer release) is kept by
            // its raw material name; the loader resolves it when it can.
            out.put("type", stack.getType().name().toLowerCase(Locale.ROOT));
        }
        short data = identityData(stack);
        if (data != 0) {
            out.put("data", (int) data);
        }
        if (stack.getAmount() != 1) {
            out.put("amount", stack.getAmount());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                out.put("name", toAmpersand(meta.getDisplayName()));
            }
            if (meta.hasLore()) {
                List<String> lines = new ArrayList<String>();
                for (String line : meta.getLore()) {
                    lines.add(toAmpersand(line));
                }
                out.put("lore", lines);
            }
        }
        Map<Enchantment, Integer> enchants = stack.getEnchantments();
        if (!enchants.isEmpty()) {
            Map<String, Object> names = new LinkedHashMap<String, Object>();
            for (Map.Entry<Enchantment, Integer> enchant : enchants.entrySet()) {
                if (enchant.getKey() != null) {
                    names.put(enchant.getKey().getName().toLowerCase(Locale.ROOT), enchant.getValue());
                }
            }
            out.put("enchants", names);
        }
        if (item.getBuyPrice() >= 0) {
            out.put("buy", item.getBuyPrice());
        }
        if (item.getSellPrice() >= 0) {
            out.put("sell", item.getSellPrice());
        }
        if (item.getCategory() != null) {
            out.put("category", item.getCategory());
        }
        out.put("page", item.getPage());
        out.put("slot", item.getSlot());
        return out;
    }

    // ------------------------------------------------------------------
    // Decoding the canonical map form into an in-memory shop.
    // ------------------------------------------------------------------

    private ItemStack readIcon(Object raw) {
        if (raw instanceof String) {
            return itemForKey((String) raw, (short) 0, 1);
        }
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<?, ?> icon = (Map<?, ?>) raw;
        String key = asString(icon, "material");
        if (key == null) {
            return null;
        }
        short data = (short) asInt(icon, "data", 0);
        ItemStack stack = itemForKey(key, data, 1);
        if (stack == null) {
            return null;
        }
        String name = asString(icon, "name");
        if (name != null) {
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(fromAmpersand(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int attachItems(Shop shop, Object rawItems) {
        int skipped = 0;
        if (!(rawItems instanceof List)) {
            return 0;
        }
        int sequential = 0;
        for (Object raw : (List<?>) rawItems) {
            if (!(raw instanceof Map)) {
                continue;
            }
            ShopItem item = readItem(shop, (Map<?, ?>) raw, sequential);
            sequential++;
            if (item == null) {
                skipped++;
                continue;
            }
            shop.put(item);
        }
        return skipped;
    }

    private ShopItem readItem(Shop shop, Map<?, ?> entry, int sequential) {
        String key = asString(entry, "material");
        MaterialLookup.Resolved resolved = key == null ? null : materials.item(key);
        if (resolved == null && key != null) {
            // Web-generated files and hand edits often use the raw enum name in
            // any case ("STONE", "LAPIS_LAZULI"); the registry is canonical
            // lowercase, so retry once before giving up.
            resolved = materials.item(key.toLowerCase(Locale.ROOT));
        }
        Material material = resolved == null ? null : resolved.material();
        short data = resolved == null ? 0 : resolved.data();
        if (entry.containsKey("data") && entry.get("data") instanceof Number) {
            data = (short) ((Number) entry.get("data")).intValue();
        }
        if (material == null) {
            String type = asString(entry, "type");
            if (type != null) {
                try {
                    material = Material.getMaterial(type);
                    if (material == null) {
                        material = Material.matchMaterial(type);
                    }
                } catch (RuntimeException ignored) {
                    material = null;
                }
            }
            if (material == null) {
                return null;
            }
        }
        int amount = Math.max(1, asInt(entry, "amount", 1));
        ItemStack stack = new ItemStack(material, amount, data);

        ItemMeta meta = stack.getItemMeta();
        boolean hasMeta = false;
        String name = asString(entry, "name");
        if (name != null) {
            meta.setDisplayName(fromAmpersand(name));
            hasMeta = true;
        }
        Object rawLore = entry.get("lore");
        if (rawLore instanceof List) {
            List<String> lines = new ArrayList<String>();
            for (Object line : (List<?>) rawLore) {
                if (line != null) {
                    lines.add(fromAmpersand(line.toString()));
                }
            }
            meta.setLore(lines);
            hasMeta = true;
        }
        if (hasMeta) {
            stack.setItemMeta(meta);
        }

        Object rawEnchants = entry.get("enchants");
        int enchantSkipped = 0;
        if (rawEnchants instanceof Map) {
            for (Map.Entry<?, ?> pair : ((Map<?, ?>) rawEnchants).entrySet()) {
                Enchantment enchant = pair.getKey() == null ? null : Enchantment.getByName(pair.getKey().toString());
                if (enchant == null && pair.getKey() != null) {
                    enchant = Enchantment.getByName(pair.getKey().toString().replace('_', ' '));
                }
                int level = pair.getValue() instanceof Number ? ((Number) pair.getValue()).intValue() : 1;
                if (enchant != null && level > 0) {
                    stack.addUnsafeEnchantment(enchant, level);
                } else {
                    enchantSkipped++;
                }
            }
        }
        if (enchantSkipped > 0) {
            log.warning("Ignored " + enchantSkipped + " enchantment(s) this server does not know in "
                    + FILE_NAME + ".");
        }

        double buy = asDouble(entry, "buy", ShopItem.NOT_OFFERED);
        double sell = asDouble(entry, "sell", ShopItem.NOT_OFFERED);
        String category = asString(entry, "category");
        int page = entry.containsKey("page") ? asInt(entry, "page", 0) : sequential / Shop.SLOTS_PER_PAGE;
        int slot = entry.containsKey("slot") ? asInt(entry, "slot", 0) : sequential % Shop.SLOTS_PER_PAGE;
        return new ShopItem(shop.getId(), page, slot, stack, buy, sell, category);
    }

    // ------------------------------------------------------------------
    // The default catalog shop for a fresh install.
    // ------------------------------------------------------------------

    private List<Shop> defaultShops() {
        List<Shop> shops = new ArrayList<Shop>();
        Shop spawn = new Shop(-1, "Spawn", defaultIcon("Spawn Shop"), Currency.WALLET, ShopType.GLOBAL, null);
        growable.add(key(spawn.getName()));
        Set<Material> present = new HashSet<Material>();
        int slot = 0;
        for (Catalog.Entry entry : Catalog.entriesFor(version)) {
            MaterialLookup.Resolved resolved = materials.item(entry.materialKey);
            if (resolved == null || !isItem(resolved.material())) {
                continue;
            }
            ShopItem item = new ShopItem(spawn.getId(), slot / Shop.SLOTS_PER_PAGE, slot % Shop.SLOTS_PER_PAGE,
                    named(resolved.material(), resolved.data(), ChatColor.WHITE + entry.display),
                    entry.buy, entry.sell, entry.category.key());
            spawn.put(item);
            slot++;
            present.add(resolved.material());
        }
        fillEveryMaterial(spawn, present);
        shops.add(spawn);
        return shops;
    }

    private ItemStack defaultIcon(String shopName) {
        Material emerald = materials.get("emerald");
        return named(emerald == null ? Material.EMERALD : emerald, ChatColor.GREEN + shopName);
    }

    private ItemStack itemForKey(String key, short data, int amount) {
        Material material = materials.material(key);
        if (material == null) {
            return null;
        }
        ItemStack stack = data == (short) 0 ? new ItemStack(material, amount) : new ItemStack(material, amount, data);
        if (!isItem(stack.getType())) {
            return null;
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Small helpers.
    // ------------------------------------------------------------------

    private short identityData(ItemStack stack) {
        if (version.isAtLeast(1, FLATTEN)) {
            return 0;
        }
        // Wear is cosmetic, not identity: a damaged legacy tool is keyed by its
        // material alone (see the reverse lookup's data-0 fallback), so it never
        // republishes its durability across an era boundary. Only non-damageable
        // items carry a real identity data value (dye index, wood type, skull type).
        if (stack.getType().getMaxDurability() > 0) {
            return 0;
        }
        return stack.getDurability();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String asString(Map<?, ?> map, String name) {
        Object value = map.get(name);
        return value == null ? null : value.toString();
    }

    private static String asString(Map<?, ?> map, String name, String def) {
        String value = asString(map, name);
        return value == null || value.isEmpty() ? def : value;
    }

    private static int asInt(Map<?, ?> map, String name, int def) {
        Object value = map.get(name);
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    private static double asDouble(Map<?, ?> map, String name, double def) {
        if (!map.containsKey(name)) {
            return def;
        }
        Object value = map.get(name);
        return value instanceof Number ? ((Number) value).doubleValue() : def;
    }

    private static boolean asBool(Map<?, ?> map, String name, boolean def) {
        Object value = map.get(name);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : def;
    }

    private static ItemStack named(Material material, short data, String name) {
        ItemStack item = data == 0 ? new ItemStack(material) : new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material material, String name) {
        return named(material, (short) 0, name);
    }

    /** Converts live {@code &sect;} colour codes to their {@code &} file form. */
    private static String toAmpersand(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (ChatColor.getByChar(code) != null) {
                    out.append('&').append(code);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String fromAmpersand(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Whether {@code material} can form an {@link ItemStack}; see the same
     * guard in the seeder. {@code isItem()} only exists from 1.13 on - on older
     * servers every material is a valid item, so the reflection fails open.
     */
    private static boolean isItem(Material material) {
        try {
            return ((Boolean) Material.class.getMethod("isItem").invoke(material)).booleanValue();
        } catch (NoSuchMethodException preThirteen) {
            return true;
        } catch (Exception unexpected) {
            return true;
        }
    }
}