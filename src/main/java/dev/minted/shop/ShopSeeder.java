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
 * Seeds the two shops a fresh install needs: the admin {@code Spawn} global
 * shop, stocked from the version-aware {@link Catalog} (so a 1.8 server never
 * gets elytra), and the single {@code Community} marketplace. The community
 * shop is (re)created whenever it is missing, which is how a database upgraded
 * from before v0.10.0 gains one without a wipe.
 *
 * <p>The global shop is also kept in step with the catalog on every load: any
 * entry the running version supports that the shop does not already sell is
 * appended (never overwriting or reordering what an admin set up), so an
 * install seeded by an older catalog gains the newer items automatically.
 */
final class ShopSeeder {

    private ShopSeeder() {
    }

    static void seed(ShopService shops, ServerVersion version, MaterialLookup materials, boolean fresh) {
        if (fresh) {
            seedGlobal(shops, version, materials);
        }
        seedMissing(shops, version, materials);
        repairGlobalShops(shops, version, materials);
        if (shops.community() == null) {
            seedCommunity(shops, materials);
        }
    }

    private static void seedGlobal(ShopService shops, ServerVersion version, MaterialLookup materials) {
        Shop shop = shops.create("Spawn", named(Material.EMERALD, ChatColor.GREEN + "Spawn Shop"), Currency.WALLET);
        int slot = 0;
        for (Catalog.Entry entry : Catalog.entriesFor(version)) {
            MaterialLookup.Resolved resolved = materials.item(entry.materialKey);
            if (resolved == null || !isItem(resolved.material())) {
                continue;
            }
            ShopItem item = new ShopItem(shop.getId(), slot / Shop.SLOTS_PER_PAGE, slot % Shop.SLOTS_PER_PAGE,
                    named(resolved.material(), resolved.data(), ChatColor.WHITE + entry.display),
                    entry.buy, entry.sell, entry.category.key());
            shops.saveItem(shop, item);
            slot++;
        }
    }

    /**
     * Appends any catalog entry the running version supports that a global shop
     * does not already sell. Runs for every global shop (not just the first),
     * is idempotent: existing items - including admin price edits - are
     * untouched, and once an entry is present it is never re-appended. This is
     * what grows an older install's global shops.
     */
    private static void seedMissing(ShopService shops, ServerVersion version, MaterialLookup materials) {
        List<Catalog.Entry> entries = Catalog.entriesFor(version);
        boolean resolutionWarned = false;
        for (Shop shop : shops.all()) {
            if (shop.getType() != ShopType.GLOBAL) {
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
            if (shop.getType() != ShopType.GLOBAL) {
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

    private static void seedCommunity(ShopService shops, MaterialLookup materials) {
        Material chest = materials.get("chest");
        ItemStack icon = named(chest == null ? Material.CHEST : chest, ChatColor.YELLOW + "Community Market");
        shops.create("Community", icon, Currency.WALLET, ShopType.COMMUNITY);
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
