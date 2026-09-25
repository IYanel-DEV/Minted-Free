package dev.minted.shop;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.shop.catalog.Catalog;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the single {@code Community} marketplace, which is (re)created
 * whenever it is missing - how a database upgraded from before v0.10.0 gains
 * one without a wipe - and keeps legacy database global shops in step with the
 * catalog. Admin global shops are no longer seeded here: they live in
 * {@code global-shops.yml} (see {@link dev.minted.shop.storage.GlobalShopFile}),
 * which creates its own default catalog file on a fresh install and grows
 * {@code catalog: true} shops on load. Every pass below deliberately skips
 * file-backed shops - everything GLOBAL-without-an-owner is file-managed.
 */
final class ShopSeeder {

    private ShopSeeder() {
    }

    static void seed(ShopService shops, ServerVersion version, MaterialLookup materials) {
        String mode = shops.getPlugin().getConfig().getString("shops.mode", "default");
        boolean communityMode = "community".equalsIgnoreCase(mode);
        
        if (!communityMode) {
            seedMissing(shops, version, materials);
            repairGlobalShops(shops, version, materials);
        }
        if (shops.community() == null) {
            seedCommunity(shops, materials, communityMode);
        }
    }

    /**
     * Appends any catalog entry the running version supports that a global shop
     * does not already sell. Runs for every database-managed global shop, is
     * idempotent: existing items - including admin price edits - are untouched,
     * and once an entry is present it is never re-appended. File-backed global
     * shops are skipped; their growth follows the {@code catalog: true} file flag.
     */
    private static void seedMissing(ShopService shops, ServerVersion version, MaterialLookup materials) {
        List<Catalog.Entry> entries = Catalog.entriesFor(version);
        boolean resolutionWarned = false;
        for (Shop shop : shops.all()) {
            if (shop.getType() != ShopType.GLOBAL || shops.isFileBacked(shop)) {
                continue;
            }
            Set<Material> present = new HashSet<Material>();
            for (ShopItem item : shop.allItems()) {
                present.add(item.raw().getType());
            }
            int added = 0;
            List<String> unresolved = new ArrayList<String>();
            for (Catalog.Entry entry : entries) {
                MaterialLookup.Resolved resolved = materials.item(entry.materialKey);
                if (resolved == null) {
                    if (unresolved.size() < 25) {
                        unresolved.add(entry.materialKey);
                    }
                    continue;
                }
                Material material = resolved.material();
                if (!isItem(material) || present.contains(material)) {
                    continue;
                }
                int address = shop.firstFreeAddress();
                if (address < 0) {
                    break;
                }
                ShopItem item = new ShopItem(shop.getId(), address / Shop.SLOTS_PER_PAGE,
                        address % Shop.SLOTS_PER_PAGE,
                        named(material, resolved.data(), ChatColor.WHITE + entry.display),
                        entry.buy, entry.sell, entry.category.key());
                shops.saveItem(shop, item);
                present.add(material);
                added++;
            }
            if (added > 0) {
                shops.info("Added " + added + " new item(s) to the global shop '" + shop.getName() + "'.");
            }
            if (!unresolved.isEmpty() && !resolutionWarned) {
                resolutionWarned = true;
                shops.warn("Some catalog items could not be resolved on this server and were skipped"
                        + " (first " + unresolved.size() + "): " + String.join(", ", unresolved) + ".");
            }
        }
    }

    /**
     * Re-resolves any global-shop row whose stored item carries a family's base
     * variant instead of the distinct one. Rows written by an older Minted on an
     * older server (or by a build whose modern material lookup collapsed) kept a
     * legacy material plus its data value; reread on 1.13+ the data is ignored,
     * so a seeded "Red Wool" decodes as WHITE_WOOL, every head as a skeleton.
     * Each seeded row is named exactly {@code §f<entry.display>}, which survives
     * the round trip - so the catalog's display-to-material map picks the row up,
     * rewrites its type to this server's correct variant, and saves it back.
     * The pass is precise (only rows named like a catalog entry) and idempotent
     * (once the type matches, nothing more is written). Admin-stocked and player
     * rows are never touched.
     */
    private static void repairGlobalShops(ShopService shops, ServerVersion version, MaterialLookup materials) {
        Map<String, Material> expected = new HashMap<String, Material>();
        for (Catalog.Entry entry : Catalog.entriesFor(version)) {
            MaterialLookup.Resolved resolved = materials.item(entry.materialKey);
            if (resolved == null || resolved.material() == null) {
                continue;
            }
            expected.put(entry.display, resolved.material());
        }

        for (Shop shop : shops.all()) {
            if (shop.getType() != ShopType.GLOBAL || shops.isFileBacked(shop)) {
                continue;
            }
            int fixed = 0;
            for (ShopItem item : shop.allItems()) {
                ItemStack stored = item.raw();
                if (stored == null) {
                    continue;
                }
                ItemMeta meta = stored.getItemMeta();
                if (meta == null || !meta.hasDisplayName()) {
                    continue;
                }
                Material correct = expected.get(ChatColor.stripColor(meta.getDisplayName()));
                if (correct == null || stored.getType() == correct) {
                    continue;
                }
                ItemStack replacement = new ItemStack(correct, stored.getAmount());
                ItemMeta replacementMeta = replacement.getItemMeta();
                replacementMeta.setDisplayName(meta.getDisplayName());
                replacement.setItemMeta(replacementMeta);
                item.setItem(replacement);
                shops.saveItem(shop, item);
                fixed++;
            }
            if (fixed > 0) {
                shops.info("Repaired " + fixed + " catalog item(s) in '" + shop.getName()
                        + "' to their correct variants.");
            }
        }
    }

    private static void seedCommunity(ShopService shops, MaterialLookup materials, boolean communityMode) {
        Material chest = materials.get("chest");
        ItemStack icon = named(chest == null ? Material.CHEST : chest, ChatColor.YELLOW + "Community Market");
        Shop community = shops.create("Community", icon, Currency.WALLET, ShopType.COMMUNITY);
        // In community mode, the marketplace is the ONLY shop - no catalog auto-fill
        if (!communityMode) {
            // Default mode: community marketplace is a catalog that grows
            // (No special action needed - the marketplace naturally shows player stock)
        }
        // In community mode: no catalog: true, player-to-player only
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

    /**
     * Whether {@code material} can form an {@link ItemStack}. {@code isItem()}
     * only exists from 1.13 on; on older servers every material is a valid item,
     * so the reflective call fails open to {@code true}. This guards against the
     * lenient name fallbacks resolving a key to a block-only material (e.g. the
     * {@code CARROTS} crop on 1.13+) which would make {@code new ItemStack}
     * throw and abort the seeding task.
     */
    private static boolean isItem(Material material) {
        try {
            return ((Boolean) Material.class.getMethod("isItem").invoke(material)).booleanValue();
        } catch (NoSuchMethodException preThirteen) {
            return true;
        } catch (Exception unexpected) {
            return true; // fail open; the ItemStack constructor stays the final authority
        }
    }
}
