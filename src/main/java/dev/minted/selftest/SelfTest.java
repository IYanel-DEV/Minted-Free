package dev.minted.selftest;

import dev.minted.MintedPlugin;
import dev.minted.bank.Amounts;
import dev.minted.bank.BankAccount;
import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.integration.customitems.CustomItems;
import dev.minted.shop.Currency;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopItem;
import dev.minted.shop.ShopType;
import dev.minted.shop.storage.GlobalShopFile;
import dev.minted.vip.VipEntry;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * One catalogue of self-tests with two runners: headless during the build
 * ({@code mvn test}, which also runs inside {@code mvn package}) and live on
 * a running server ({@code /minted selftest}). "Correct" is defined here
 * once, so the two can never drift apart.
 *
 * <p>Every check is total: it reports pass, skip or a short reason, and can
 * never throw into the caller. The live half additionally exercises what only
 * a real server can prove - the configured database, the registered commands,
 * the loaded shop model and the Redis channel.
 */
public final class SelfTest {

    /** UNIT: pure rules, safe headless. LIVE: those plus the running server. */
    public enum Mode {
        UNIT,
        LIVE
    }

    private SelfTest() {
    }

    /** The outcome of a whole run: counters, a report line per check, failures. */
    public static final class Result {

        private final List<String> lines = new ArrayList<String>();
        private final List<String> failures = new ArrayList<String>();
        private int passed;
        private int skipped;

        public void add(SelfCheck check) {
            SelfCheck.Outcome outcome = check.run();
            String detail = outcome.detail() == null ? "" : outcome.detail();
            if (outcome.isPassed()) {
                passed++;
                lines.add("PASS | " + check.name());
            } else if (outcome.isSkipped()) {
                skipped++;
                lines.add("SKIP | " + check.name() + (detail.isEmpty() ? "" : " | " + detail));
            } else {
                failures.add(check.name() + " -> " + detail);
                lines.add("FAIL | " + check.name() + (detail.isEmpty() ? "" : " | " + detail));
            }
        }

        /** Adds and runs a check body in one call. */
        public void check(String name, SelfCheck.Body body) {
            add(SelfCheck.of(name, body));
        }

        public boolean isOk() {
            return failures.isEmpty();
        }

        public int passed() {
            return passed;
        }

        public int skipped() {
            return skipped;
        }

        public int total() {
            return lines.size();
        }

        public List<String> lines() {
            return lines;
        }

        public List<String> failures() {
            return failures;
        }

        public String summary() {
            return passed + "/" + total() + " passed"
                    + (skipped > 0 ? ", " + skipped + " skipped" : "")
                    + (failures.isEmpty() ? "" : ", " + failures.size() + " FAILED");
        }
    }

    public static Result run(Mode mode, MintedPlugin plugin) {
        Result result = new Result();
        for (SelfCheck check : catalogue(mode, plugin)) {
            result.add(check);
        }
        return result;
    }

    /** The checks for a mode; the live run always includes the pure rules. */
    public static List<SelfCheck> catalogue(Mode mode, MintedPlugin plugin) {
        List<SelfCheck> checks = new ArrayList<SelfCheck>();
        unitChecks(checks);
        if (mode == Mode.LIVE && plugin != null) {
            liveChecks(checks, plugin);
        }
        return checks;
    }

    private static void unitChecks(List<SelfCheck> out) {
        out.add(SelfCheck.of("amount tokens: all, half, max, number, garbage", () -> {
            require(Amounts.resolve("all", 120, 50) == 120, "all should be the available amount");
            require(Amounts.resolve("half", 121, 50) == 60.5, "half should be half");
            require(Amounts.resolve("max", 120, 50) == 50, "max should be the smaller of the two");
            require(Amounts.resolve("42.5", 120, 50) == 42.5, "a number should parse");
            require(Amounts.resolve("banana", 120, 50) < 0, "garbage should be refused");
            require(Amounts.resolve("NaN", 120, 50) < 0, "NaN should be refused");
            require(Amounts.resolve("Infinity", 120, 50) < 0, "Infinity should be refused");
        }));

        out.add(SelfCheck.of("account rules: deposit, cap, withdraw, overdraft", () -> {
            BankAccount account = BankAccount.detached(UUID.randomUUID(), 100, 1000);
            require(account.getBalance() == 100, "wrong starting balance");
            require(account.deposit(50) && account.getBalance() == 150, "deposit did not apply");
            require(!account.deposit(0) && !account.deposit(-5), "non-positive deposits must be refused");
            require(!account.deposit(900), "the cap must be enforced in memory");
            require(account.withdraw(150) && account.getBalance() == 0, "withdraw failed");
            require(!account.withdraw(1), "an overdraft must be refused");
        }));

        out.add(SelfCheck.of("account save state: admin set is absolute, synced clears it", () -> {
            BankAccount account = BankAccount.detached(UUID.randomUUID(), 10, 1000);
            require(account.setBalance(500), "an admin set inside the cap must be accepted");
            require(account.isAbsolute(), "an admin set must be written outright");
            account.synced(500);
            require(!account.isAbsolute(), "synced() must clear the absolute flag");
            require(!account.isDirty(), "synced() must clear the dirty flag");
        }));

        out.add(SelfCheck.of("account sync: a remote value never clobbers unsaved work", () -> {
            BankAccount account = BankAccount.detached(UUID.randomUUID(), 10, 1000);
            account.deposit(5);
            account.remoteRefresh(999);
            require(account.getBalance() == 15, "a remote refresh overwrote a local change");
            account.synced(15);
            account.remoteRefresh(42);
            require(account.getBalance() == 42, "a clean account must adopt the remote value");
            require(account.getPersisted() == 42, "the stored baseline must follow the remote value");
        }));

        out.add(SelfCheck.of("multi-server: deltas compose, overdraft and cap are refused", () -> {
            double ceiling = 1000;
            UUID id = UUID.randomUUID();
            Table table = new Table();
            table.seed(id, 500);
            // Two servers cached the same 500 and both deposit 50.
            BankAccount serverA = BankAccount.detached(id, 500, ceiling);
            BankAccount serverB = BankAccount.detached(id, 500, ceiling);
            require(serverA.deposit(50) && serverB.deposit(50), "deposits refused in memory");
            serverA.synced(table.apply(id, serverA.getBalance() - serverA.getPersisted(), 0, ceiling));
            serverB.synced(table.apply(id, serverB.getBalance() - serverB.getPersisted(), 0, ceiling));
            require(table.load(id) == 600,
                    "concurrent deposits overwrote each other, row is " + table.load(id));

            // Server A spends 100; server B still believes it holds 600 and spends all of it.
            require(serverA.withdraw(100), "withdraw refused in memory");
            serverA.synced(table.apply(id, serverA.getBalance() - serverA.getPersisted(), 0, ceiling));
            require(table.load(id) == 500, "withdraw did not apply, row is " + table.load(id));
            require(serverB.withdraw(600), "B's cache says 600, so its withdraw passes in memory");
            serverB.synced(table.apply(id, serverB.getBalance() - serverB.getPersisted(), 0, ceiling));
            require(table.load(id) == 500,
                    "a stale cache must not move the shared balance, row is " + table.load(id));
            require(serverB.getBalance() == 500, "B must be corrected to the truth, is " + serverB.getBalance());

            // Corrected, B can now spend exactly what it really holds.
            require(serverB.withdraw(500), "B cannot spend its corrected balance");
            serverB.synced(table.apply(id, serverB.getBalance() - serverB.getPersisted(), 0, ceiling));
            require(table.load(id) == 0, "the corrected balance should be spendable, row is " + table.load(id));

            // A stale cache must not push a balance past the cap either.
            table.seed(id, 950);
            BankAccount stale = BankAccount.detached(id, 500, ceiling);
            require(stale.deposit(100), "deposit refused in memory");
            stale.synced(table.apply(id, stale.getBalance() - stale.getPersisted(), 0, ceiling));
            require(table.load(id) == 950, "the cap was breached, row is " + table.load(id));
            require(stale.getBalance() == 950, "the stale cache must be corrected to the cap");
        }));

        out.add(SelfCheck.of("shop matching: material and durability rules, no custom items", () -> {
            // Resolved by name, never as a Material constant: a legacy-only name
            // (WOOL, INK_SACK, ...) would throw NoSuchFieldError on 1.13+, while
            // this resolution simply skips with a reason.
            Material apple = material("apple");
            Material dirt = material("dirt");
            Material sword = material("diamond_sword");
            require(ShopItem.sameStock(new ItemStack(apple), new ItemStack(apple)),
                    "two apples must match");
            require(!ShopItem.sameStock(new ItemStack(apple), new ItemStack(dirt)),
                    "different materials must not match");
            ItemStack usedSword = new ItemStack(sword);
            usedSword.setDurability((short) 5);
            ItemStack freshSword = new ItemStack(sword);
            freshSword.setDurability((short) 9);
            require(ShopItem.sameStock(usedSword, freshSword), "tool damage must not change identity");
        }));

        out.add(SelfCheck.of("shop matching: legacy data value on non-durable items", () -> {
            // The data-value rule inside sameStock only exists for the legacy
            // era: from 1.13 on, item data lives in components, and setting a
            // durability on a non-damageable item is silently discarded. Probe
            // the platform and skip with a reason instead of asserting a rule
            // that cannot apply here.
            Material stone = material("stone");
            ItemStack dataValued = new ItemStack(stone);
            dataValued.setDurability((short) 14);
            if (dataValued.getDurability() == 0) {
                throw new SelfCheck.SkipSignal(
                        "this platform stores item data as components (1.13+), so the legacy rule does not apply");
            }
            require(!ShopItem.sameStock(dataValued, new ItemStack(stone)),
                    "a legacy data value must change identity on non-durable items");
        }));

        out.add(SelfCheck.of("custom items: no provider means vanilla matching", () -> {
            require(CustomItems.identity(null) == null, "a null stack has no identity");
            require(CustomItems.identity(new ItemStack(material("stone"))) == null,
                    "a vanilla stack must have no custom identity");
        }));

        out.add(SelfCheck.of("server version: parsing is deterministic and 26.x re-maps onto 1.x", () -> {
            require(ServerVersion.parse("1.8.9-R0.1-SNAPSHOT").toString().equals("1.8.9"),
                    "a 1.8.9 snapshot should read as 1.8.9");
            require(ServerVersion.parse("1.8.9-R0.1-SNAPSHOT").isAtLeast(1, 8)
                            && !ServerVersion.parse("1.7.10").isSupported(),
                    "support should gate at 1.8");
            require(ServerVersion.parse("26.2-R0.1-SNAPSHOT").toString().equals("1.26.2"),
                    "'26.2' names a server after the leading '1.' was dropped: it is really 1.26.2");
            require(ServerVersion.parse("26.2-R0.1-SNAPSHOT").isAtLeast(1, 21)
                            && !ServerVersion.parse("12.3").isAtLeast(1, 26),
                    "the 1.x gate must hold for the post-1.26 naming");
        }));

        out.add(SelfCheck.of("vip: only real player names can be commands", () -> {
            require(VipEntry.isCommandName("Notch"), "a normal name must be accepted");
            require(VipEntry.isCommandName("Player_123"), "underscores and digits must be accepted");
            require(!VipEntry.isCommandName("bad name"), "a name with a space must be refused");
            require(!VipEntry.isCommandName("ab"), "a too-short name must be refused");
            require(!VipEntry.isCommandName(null), "null must be refused");
        }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** A stack for a registry key at an amount, with a display name, resolved version-safe. */
    private static ItemStack namedStack(MaterialLookup lookup, String key, int amount, String name) {
        MaterialLookup.Resolved resolved = lookup.item(key);
        require(resolved != null, key + " must resolve");
        ItemStack stack = new ItemStack(resolved.material(), amount, resolved.data());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A temp folder for file round-trip checks, cleaned by the caller. */
    private static File freshDir() {
        try {
            return Files.createTempDirectory("minted-selftest").toFile();
        } catch (IOException e) {
            throw new AssertionError("could not create a temp directory", e);
        }
    }

    private static void deleteTree(File dir) {
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /** One hand-written items entry: material key, buy/sell (negative = closed), optional data. */
    private static Map<String, Object> fileEntry(String material, double buy, double sell, Integer data) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("material", material);
        if (buy >= 0) {
            entry.put("buy", buy);
        }
        if (sell >= 0) {
            entry.put("sell", sell);
        }
        if (data != null) {
            entry.put("data", data);
        }
        return entry;
    }

    /** One hand-written items entry keyed by its raw legacy type name. */
    private static Map<String, Object> typeEntry(String type) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("type", type);
        return entry;
    }

    /**
     * Resolves a material by name instead of through a {@code Material}
     * constant. Constants are compiled as direct field reads, so a legacy-only
     * name throws {@code NoSuchFieldError} on 1.13+; resolving by name lets the
     * check skip with a readable reason instead.
     */
    private static Material material(String name) {
        Material resolved = Material.matchMaterial(name);
        if (resolved == null) {
            throw new SelfCheck.SkipSignal("'" + name + "' does not exist on this server version");
        }
        return resolved;
    }

    /** The guarded-delta contract of {@code AccountDao}, modelled in memory. */
    private static final class Table {

        private final Map<UUID, Double> rows = new HashMap<UUID, Double>();

        void seed(UUID id, double balance) {
            rows.put(id, balance);
        }

        Double load(UUID id) {
            return rows.get(id);
        }

        /** Mirrors {@code AccountDao.applyDelta} exactly. */
        Double apply(UUID id, double delta, double seed, double ceiling) {
            Double current = rows.get(id);
            if (current != null) {
                double next = current + delta;
                if (next >= 0 && next <= ceiling) {
                    rows.put(id, next);
                    return next;
                }
                return current;
            }
            double created = seed + delta;
            if (created < 0 || created > ceiling) {
                return null;
            }
            rows.put(id, created);
            return created;
        }
    }

    private static void liveChecks(List<SelfCheck> out, MintedPlugin plugin) {
        out.add(SelfCheck.of("global shop file: a fresh install seeds, loads, and grows only once", () -> {
            ServerVersion version = plugin.getServerVersion();
            MaterialLookup lookup = new MaterialLookup(version);
            File dir = freshDir();
            try {
                GlobalShopFile file = new GlobalShopFile(dir, version, lookup,
                        Logger.getLogger("minted-selftest"));
                require(!file.exists(), "a fresh install must have no shop file yet");
                file.writeDefault();
                require(file.exists(), "writeDefault() must create the file");
                List<Shop> loaded = file.load(20);
                require(loaded.size() == 1, "the default file should hold exactly one shop, got " + loaded.size());
                Shop spawn = loaded.get(0);
                require(spawn.getType() == ShopType.GLOBAL && spawn.getOwner() == null,
                        "a file shop must be an admin global shop");
                int stocked = spawn.allItems().size();
                require(stocked > 0, "the default shop should be stocked with this version's catalog");
                require(file.wantsGrowth(spawn), "the default shop must be catalog:true (automatic growth)");
                int wholeVersion = GlobalShopFile.sellableMaterials().size();
                require(stocked >= wholeVersion,
                        "the default shop should sell every material of the version (got " + stocked
                                + ", expected at least " + wholeVersion + ")");
                file.grow(spawn);
                file.grow(spawn);
                require(spawn.allItems().size() == stocked,
                        "growth must be idempotent: the seeded catalog already covers every entry");
            } finally {
                deleteTree(dir);
            }
        }));

        out.add(SelfCheck.of("global shop file: page, slot, prices and cosmetics round-trip", () -> {
            ServerVersion version = plugin.getServerVersion();
            MaterialLookup lookup = new MaterialLookup(version);
            Material emerald = lookup.get("emerald");
            Material wool = lookup.get("wool");
            require(emerald != null && wool != null, "emerald and wool must resolve on this server");
            final short expectedData = version.isAtLeast(1, 13) ? (short) 0 : (short) 14;
            Enchantment sharpness = Enchantment.getByName("SHARPNESS");
            require(sharpness != null, "sharpness must be registered on this server");
            if (lookup.item("diamond_sword") == null) {
                throw new SelfCheck.SkipSignal("diamond_sword does not resolve on this server");
            }
            File dir = freshDir();
            try {
                GlobalShopFile file = new GlobalShopFile(dir, version, lookup,
                        Logger.getLogger("minted-selftest"));
                Shop shop = new Shop(7, "Test", new ItemStack(emerald), Currency.WALLET, ShopType.GLOBAL, null);
                ItemStack sword = namedStack(lookup, "diamond_sword", 3, ChatColor.RED + "Blade of Testing");
                sword.addUnsafeEnchantment(sharpness, 5);
                ItemMeta swordMeta = sword.getItemMeta();
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + "Line one");
                lore.add(ChatColor.DARK_GRAY + "Line two");
                swordMeta.setLore(lore);
                sword.setItemMeta(swordMeta);
                shop.put(new ShopItem(7, 1, 7, sword, 12.5, ShopItem.NOT_OFFERED, "tools"));
                ItemStack dyedWool = new ItemStack(wool, 1, expectedData == 0 ? (short) 1 : expectedData);
                ItemMeta woolMeta = dyedWool.getItemMeta();
                woolMeta.setDisplayName(ChatColor.GREEN + "Red Wool");
                dyedWool.setItemMeta(woolMeta);
                shop.put(new ShopItem(7, 3, 0, dyedWool, ShopItem.NOT_OFFERED, 2.25, "nature"));

                List<Shop> single = new ArrayList<Shop>();
                single.add(shop);
                file.save(single);
                List<Shop> loaded = file.load(1000);
                require(loaded.size() == 1, "one shop should come back, got " + loaded.size());
                Shop got = loaded.get(0);
                require(got.getId() == 1000, "file ids are assigned from idBase, never stored");
                require(got.getIcon().getType() == emerald, "the shop icon must round-trip");
                require(got.getCurrency() == Currency.WALLET, "the currency must round-trip");

                ShopItem blade = got.itemAt(1, 7);
                require(blade != null, "the sword must come back at page 1 slot 7");
                require(blade.getBuyPrice() == 12.5, "the buy price must round-trip");
                require(blade.getSellPrice() == ShopItem.NOT_OFFERED, "a closed sell side must stay closed");
                require("tools".equals(blade.getCategory()), "the category must round-trip");
                ItemStack bladeBack = blade.copy();
                require(bladeBack.getAmount() == 3, "the amount must round-trip");
                require(bladeBack.getItemMeta().hasDisplayName()
                        && (ChatColor.RED + "Blade of Testing").equals(bladeBack.getItemMeta().getDisplayName()),
                        "the name must round-trip");
                require(bladeBack.getItemMeta().hasLore()
                        && bladeBack.getItemMeta().getLore().size() == 2
                        && (ChatColor.GRAY + "Line one").equals(bladeBack.getItemMeta().getLore().get(0)),
                        "the lore must round-trip");
                require(bladeBack.containsEnchantment(sharpness)
                        && bladeBack.getEnchantmentLevel(sharpness) == 5,
                        "the enchantment must round-trip");

                ShopItem dyed = got.itemAt(3, 0);
                require(dyed != null, "the wool item must come back at page 3 slot 0");
                require(dyed.getBuyPrice() == ShopItem.NOT_OFFERED, "a closed buy side must stay closed");
                require(dyed.getSellPrice() == 2.25, "the sell price must round-trip");
                require(dyed.copy().getType() == wool && dyed.copy().getDurability() == expectedData,
                        "the identity data must stay distinct for this era");

                file.save(loaded);
                List<Shop> again = file.load(1000);
                require(again.size() == 1 && again.get(0).allItems().size() == 2,
                        "a rewrite must not lose or drift items");
                ShopItem restocked = again.get(0).itemAt(1, 7);
                require(restocked != null && restocked.getBuyPrice() == 12.5,
                        "a rewrite must keep the address and price stable");
            } finally {
                deleteTree(dir);
            }
        }));

        out.add(SelfCheck.of("global shop file: unknown and foreign items are skipped, the rest survives", () -> {
            ServerVersion version = plugin.getServerVersion();
            MaterialLookup lookup = new MaterialLookup(version);
            Material emerald = lookup.get("emerald");
            Material wool = lookup.get("wool");
            require(emerald != null && wool != null, "emerald and wool must resolve on this server");
            final short expectedData = version.isAtLeast(1, 13) ? (short) 0 : (short) 14;
            // Whether netherite can stock this shop: it lands at (0,0) when it can,
            // leaves the slot empty when it cannot - either way the items after it
            // keep the same addresses, because skipped rows still burn a sequence.
            boolean netheriteArrives = lookup.available("netherite_ingot");
            File dir = freshDir();
            try {
                GlobalShopFile file = new GlobalShopFile(dir, version, lookup,
                        Logger.getLogger("minted-selftest"));
                Map<String, Object> custom = new LinkedHashMap<String, Object>();
                custom.put("name", "Custom");
                custom.put("currency", "bank");
                custom.put("catalog", true);
                Map<String, Object> icon = new LinkedHashMap<String, Object>();
                icon.put("material", "emerald");
                custom.put("icon", icon);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                items.add(fileEntry("netherite_ingot", 88, -1, null)); // newer than this server can stock
                items.add(typeEntry("totally_not_real"));               // nobody has heard of it
                items.add(fileEntry("wool", 3.5, 2, 14));               // dyed wool, page/slot omitted
                items.add(fileEntry("emerald", 5, -1, null));
                Map<String, Object> upper = fileEntry("cobblestone", 1, 1, null);
                upper.put("material", "COBBLESTONE");                   // web files write raw enum names
                items.add(upper);
                custom.put("items", items);
                List<Map<String, Object>> shops = new ArrayList<Map<String, Object>>();
                shops.add(custom);
                YamlConfiguration conf = new YamlConfiguration();
                conf.set("shops", shops);
                conf.save(new File(dir, GlobalShopFile.FILE_NAME));

                List<Shop> loaded = file.load(500);
                require(loaded.size() == 1, "only the representable shop should load, got " + loaded.size());
                Shop shop = loaded.get(0);
                require(shop.getCurrency() == Currency.BANK, "bank currency must load");
                require(file.wantsGrowth(shop), "catalog:true must be honoured");
                require(shop.itemAt(0, 1) == null, "an unrepresentable item must not take an address");
                if (netheriteArrives) {
                    require(shop.itemAt(0, 0) != null, "the known item should be stocked at (0,0)");
                }
                ShopItem red = shop.itemAt(0, 2);
                require(red != null, "the dyed wool item should land at (0,2)");
                require(red.getBuyPrice() == 3.5 && red.getSellPrice() == 2, "prices must load");
                require(red.copy().getType() == wool && red.copy().getDurability() == expectedData,
                        "the identity data must survive");
                require((ChatColor.RED + "Red Wool").equals(red.copy().getItemMeta().getDisplayName()),
                        "the display name must survive");
                require(shop.itemAt(0, 3) != null, "the emerald item should land next");
                ShopItem cobble = shop.itemAt(0, 4);
                require(cobble != null && cobble.copy().getType() == lookup.get("cobblestone"),
                        "an uppercase raw material name (web export) must still resolve");
                require(shop.allItems().size() == (netheriteArrives ? 4 : 3),
                        "only representable items should load");

                Shop renamed = new Shop(1, "Renamed", new ItemStack(emerald), Currency.WALLET, ShopType.GLOBAL, null);
                file.remapGrowable("custom", "renamed");
                require(file.wantsGrowth(renamed), "the growth marker must follow a rename");
                file.drop("renamed");
                require(!file.wantsGrowth(renamed), "the growth marker must be forgotten with the shop");

                file.save(loaded);
                List<Shop> clean = file.load(500);
                require(clean.size() == 1 && clean.get(0).allItems().size() == (netheriteArrives ? 4 : 3),
                        "a rewrite should drop the skipped entries and keep the good ones");
            } finally {
                deleteTree(dir);
            }
        }));

        out.add(SelfCheck.of("storage: guarded delta round-trip (then cleaned up)", () -> {
            dev.minted.backend.StorageProvider storage = plugin.storageForSelfTest();
            if (storage == null) {
                throw new SelfCheck.SkipSignal("storage is still opening");
            }
            UUID id = UUID.randomUUID();
            try {
                require(storage.loadBalance(id) == null, "a fresh uuid must not have a row");
                Double first = storage.applyDelta(id, 25, 0, 1_000_000d);
                require(first != null && first == 25d, "the first delta should create the row at 25, got " + first);
                Double second = storage.applyDelta(id, -10, 0, 1_000_000d);
                require(second != null && second == 15d, "the second delta should compose to 15, got " + second);
                Double refused = storage.applyDelta(id, -1000, 0, 1_000_000d);
                require(refused != null && refused == 15d,
                        "an overdraft must be refused and report the truth, got " + refused);
            } finally {
                storage.deleteBalance(id);
            }
            require(storage.loadBalance(id) == null, "the self-test row was not cleaned up");
        }));

        out.add(SelfCheck.of("economy: services ready and the public API answers", () -> {
            dev.minted.api.MintedEconomy economy = dev.minted.api.MintedAPI.economy();
            if (economy == null) {
                throw new AssertionError("the public economy API is not registered");
            }
            require(economy.isReady(), "the economy is not ready yet");
            require(economy.fractionalDigits() >= 0, "fractional digits cannot be negative");
            require(economy.format(1) != null && !economy.format(1).isEmpty(), "formatting produced nothing");
            require(economy.getBalance(UUID.randomUUID()) >= 0, "a fresh account cannot be negative");
        }));

        out.add(SelfCheck.of("catalog: every category icon resolves on this server", () -> {
            dev.minted.compat.MaterialLookup lookup =
                    new dev.minted.compat.MaterialLookup(plugin.getServerVersion());
            for (dev.minted.shop.catalog.Category category
                    : dev.minted.shop.catalog.Category.values()) {
                require(lookup.available(category.iconKey()),
                        "category '" + category.key() + "' cannot resolve its icon '"
                                + category.iconKey() + "' here");
            }
        }));

        out.add(SelfCheck.of("shops: model loaded", () -> {
            if (!plugin.shopService().isReady()) {
                throw new SelfCheck.SkipSignal("shops are still loading");
            }
        }));

        out.add(SelfCheck.of("commands: executors registered", () -> {
            String[] names = {"minted", "balance", "pay", "bank", "wallet", "sell", "eshop", "pshop"};
            for (String name : names) {
                org.bukkit.command.PluginCommand command = plugin.getCommand(name);
                require(command != null, "/" + name + " is missing from plugin.yml");
                require(command.getExecutor() != null, "/" + name + " has no executor");
            }
        }));

        out.add(SelfCheck.of("vip: list readable", () -> {
            if (plugin.getVipService() == null) {
                throw new SelfCheck.SkipSignal("VIP service not present");
            }
            require(plugin.getVipService().all().size() == plugin.getVipService().size(),
                    "the roster and the counter disagree");
        }));

        out.add(SelfCheck.of("custom items: providers resolved", () -> {
            String[] providers = dev.minted.integration.customitems.CustomItems.providers();
            if (providers.length == 0) {
                throw new SelfCheck.SkipSignal("no custom item plugin installed");
            }
        }));

        out.add(SelfCheck.of("multi-server: announcement channel", () -> {
            if (!plugin.networkStatus().contains("Redis")) {
                throw new SelfCheck.SkipSignal("multi-server or Redis is off");
            }
            for (int attempt = 0; attempt < 20 && !plugin.redisConnected(); attempt++) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            require(plugin.redisConnected(),
                    "Redis was configured but never connected: " + plugin.networkStatus());
        }));
    }
}