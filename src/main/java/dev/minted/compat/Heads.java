package dev.minted.compat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Version-safe player heads for menus, including full-texture heads.
 *
 * <p>Until 1.13 a player head is the {@code SKULL_ITEM} material with a data
 * value of 3; from 1.13 it is its own {@code PLAYER_HEAD} material. A named
 * head uses {@link SkullMeta#setOwner}, which every supported release
 * understands, so a head always renders even when a server cannot resolve the
 * skin.
 *
 * <p>Textured heads are the "orb" used all over the large public head
 * libraries: a player head whose profile carries a {@code textures} property
 * pointing at a {@code textures.minecraft.net} skin. On 1.18.2+ servers that is
 * done through the official {@code org.bukkit.profile.PlayerProfile} API; older
 * servers get the same profile built reflectively through the authlib classes
 * bundled with every server and applied through {@code CraftMetaSkull#setProfile}.
 * Every version and fork behaves slightly differently, so all paths are guarded:
 * if every step fails Minted returns the {@linkplain #icon(String, Material)
 * plain-material fallback} and a menu can never break because of a head. The
 * texture values baked in here are those of the public Minecraft-Heads library
 * and are cheap for a client to fetch from Mojang's servers.
 */
public final class Heads {

    public static final short PLAYER_DATA = 3;

    // Textured icons, keyed by the UI glyph they represent. Values are base64
    // profile texture payloads from the public Minecraft-Heads library. The
    // URLs are https: modern clients refuse http texture links (HTTP 451), and
    // a head whose skin cannot load renders as the default Steve/Alex skin.
    private static final String T_BACK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2NkYzllNGRjZmE0MjIxYTFmYWRjMWI1YjJiMTFkOGJlZWI1Nzg3OWFmMWM0MjM2MjE0MmJhZTFlZGQ1In19fQ==";
    private static final String T_NEXT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzk1NmEzNjE4NDU5ZTQzYjI4N2IyMmI3ZTIzNWVjNjk5NTk0NTQ2YzZmY2Q2ZGM4NGJmY2E0Y2YzMGFiOTMxMSJ9fX0=";
    private static final String T_MONEY =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2IwYTdiOTRjNGU1ODFiNjk5MTU5ZDQ4ODQ2ZWMwOTEzOTI1MDYyMzdjODlhOTdjOTMyNDhhMGQ4YWJjOTE2ZDUifX19";
    private static final String T_BANK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2UxZWRmMTZjNDFkMTk0YzczMWUzM2ZkZDljMmI5ZTVlZGQ0NTBiYzMzY2E3MDQzNjUyOGEwNTM4OWQ3ZmNkYTIifX19";
    private static final String T_WALLET =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzZlN2UzZThhYjA2MGU2NGQwMjU2YjM2ODhlNjJkNDMzZWFiMzQxYTE1N2YyYTczM2VkNDM0NTBmZWU0ZTcyNjQifX19";
    private static final String T_POUCH =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzgzODFjNTI5ZDUyZTAzY2Q3NGMzYmYzOGJiNmJhM2ZkZTEzMzdhZTliZjUwMzMyZmFhODg5ZTBhMjhlODA4MWYifX19";
    private static final String T_HOME =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNiMGE2ODllNTU3NGFmODAzZTAxZWZkYTAxMTE0YzlkY2QzNTdlOWM0Mjc4OTY1YmI1YjRkYmI1YzMzODczNDcifX19";
    private static final String T_CONFIRM =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E3OWE1Yzk1ZWUxN2FiZmVmNDVjOGRjMjI0MTg5OTY0OTQ0ZDU2MGYxOWE0NGYxOWY4YTQ2YWVmM2ZlZTQ3NTYifX19";
    private static final String T_NONE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIzYjMyOWMxMDUyNzdiZjRhODU2MjFlM2U1YTQ5NmUyYWEzNzY1ODU2NmUwN2RmYTFjZGJiN2JiNmMxOWE1YTYifX19";
    private static final String T_PAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzI2OGUzZTNmMzkxNTRhODk3Njc4NmIyZWI2Zjc5MTA4YjkzM2ZkYjliZTAzM2JhNmFlYmNhYWFmZmYxOGUzZTMifX19";
    private static final String T_CROWN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkxYjBhNWJiYzY5N2MwYzQyYTZjZjFiOWM0YzQ0MzViMDczMjJmY2U1YmIyN2Q4MmI2OTMwODQzZTVhYjdhMDkifX19";
    private static final String T_FLAME =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2JiNThiODNmMDc2MThlYTc5YTFhMTIwMmI1YTc3YjE0ZGYxYzhlMzViNmUxZGViOGEyNmQ4OTc2Zjg1MzYwYzMifX19";
    private static final String T_BOOKS =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhZjY0NWY0ODI0ZTU0ZDI2OTcyZDBiZjcyMTFiNjgxNTM4MjFkYzNhMGRmOTkyZTc2NjVjYjIxYzY5NjkwZGEifX19";
    private static final String T_SILVER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EzNGIyN2JmY2M4ZjliOTY0NTk0YjYxOGIxMTQ2YWY2OWRlMjc4Y2U1ZTJlMzAxMmNiNDcxYTlhM2NmMzg3MSJ9fX0=";

    private Heads() {
    }

    /** The board back arrow. */
    public static String backSkin() {
        return T_BACK;
    }

    /** The page-forward arrow. */
    public static String nextSkin() {
        return T_NEXT;
    }

    /** A gold coin, for money. */
    public static String moneySkin() {
        return T_MONEY;
    }

    /** An emerald coin, for banked value. */
    public static String bankSkin() {
        return T_BANK;
    }

    /** A wallet, for held physical cash. */
    public static String walletSkin() {
        return T_WALLET;
    }

    /** A pouch of coins, for giving/receiving money. */
    public static String pouchSkin() {
        return T_POUCH;
    }

    /** A chest of gold, for the marketplace home. */
    public static String homeSkin() {
        return T_HOME;
    }

    /** A plain check mark, for confirm. */
    public static String confirmSkin() {
        return T_CONFIRM;
    }

    /** A red no-entry disc, for close/cancel. */
    public static String noneSkin() {
        return T_NONE;
    }

    /** An open book, for ledger / page reads. */
    public static String bookSkin() {
        return T_PAGE;
    }

    /** A golden crown, for the richest ranking. */
    public static String crownSkin() {
        return T_CROWN;
    }

    /** A flame, for burned / lost money. */
    public static String flameSkin() {
        return T_FLAME;
    }

    /** A small stack of books, for the sales feed. */
    public static String booksSkin() {
        return T_BOOKS;
    }

    /** A silver coin, for withdrawing cash. */
    public static String silverSkin() {
        return T_SILVER;
    }

    /**
     * A player head carrying the given profile texture, or null when texture
     * heads are not possible on this server (no head material, or the profile
     * cannot be applied). Never throws.
     */
    public static ItemStack texture(String base64) {
        ItemStack head = head("PLAYER_HEAD", "SKULL_ITEM", "LEGACY_SKULL_ITEM", PLAYER_DATA);
        if (head == null) {
            return null;
        }
        // Modern servers expose the official Bukkit profile API: on 1.18.2+
        // this renders reliably where the reflective authlib path below can be
        // blocked by Paper's obfuscation or module sealing.
        ItemStack modern = applyBukkitProfile(head, base64);
        if (modern != null) {
            return modern;
        }
        try {
            Class<?> profileClass = gameProfileClass();
            Constructor<?> profileCtor = profileClass.getConstructor(UUID.class, String.class);
            Object profile = profileCtor.newInstance(UUID.randomUUID(), "Minted");
            Object properties = profileClass.getMethod("getProperties").invoke(profile);

            Class<?> propertyClass = propertyClass(profileClass);
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", base64);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.setAccessible(true);
            put.invoke(properties, "textures", property);

            ItemMeta meta = head.getItemMeta();
            Method setProfile = findProfileMethod(meta.getClass(), profileClass);
            setProfile.setAccessible(true);
            setProfile.invoke(meta, profile);
            head.setItemMeta(meta);
            return head;
        } catch (Throwable ignored) {
            // Any failure at all - missing classes, sealed modules, odd forks -
            // must never break a menu: the caller falls back to a plain item.
            return null;
        }
    }

    /**
     * The 1.18.2+ Bukkit way of stamping a texture on a player skull: build a
     * {@code PlayerProfile}, add the {@code textures} property, and hand it to
     * {@code SkullMeta#setOwnerProfile}. Returns null when this server does not
     * have the API so the caller keeps the reflective authlib path.
     */
    private static ItemStack applyBukkitProfile(ItemStack head, String base64) {
        try {
            Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> propertyType = Class.forName("org.bukkit.profile.ProfileProperty");
            Object server = org.bukkit.Bukkit.getServer();
            Method factory;
            try {
                factory = server.getClass().getMethod("createPlayerProfile", UUID.class, String.class);
            } catch (NoSuchMethodException olderFactory) {
                factory = server.getClass().getMethod("createProfile", UUID.class, String.class);
            }
            Object profile = factory.invoke(server, UUID.randomUUID(), "Minted");
            Object properties = profileType.getMethod("getProperties").invoke(profile);
            Object property = propertyType.getConstructor(String.class, String.class)
                    .newInstance("textures", base64);
            properties.getClass().getMethod("add", propertyType).invoke(properties, property);

            ItemMeta meta = head.getItemMeta();
            org.bukkit.inventory.meta.SkullMeta.class
                    .getMethod("setOwnerProfile", profileType).invoke(meta, profile);
            head.setItemMeta(meta);
            return head;
        } catch (Throwable notModern) {
            return null;
        }
    }

    /** @return a texture head, or the fallback material when one is not possible. */
    public static ItemStack icon(String base64, Material fallback) {
        ItemStack head = texture(base64);
        return head != null ? head : new ItemStack(fallback, 1);
    }

    /**
     * A player head for {@code name}, or a plain head when the owner cannot be
     * applied, or null when no head material exists on this server at all.
     */
    public static ItemStack player(String name) {
        ItemStack head = head("PLAYER_HEAD", "SKULL_ITEM", "LEGACY_SKULL_ITEM", PLAYER_DATA);
        if (head == null || name == null || name.isEmpty()) {
            return head;
        }
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwner(name);
            head.setItemMeta(meta);
        } catch (RuntimeException ignored) {
            // A server that rejects the owner still gets a usable head icon.
        }
        return head;
    }

    /** A generic wall skull, the "board" glyph, using the modern name when it exists. */
    public static ItemStack skull() {
        return head("SKELETON_SKULL", "SKULL_ITEM", "LEGACY_SKULL_ITEM", (short) 0);
    }

    /** The authlib GameProfile class, wherever this server keeps it. */
    private static Class<?> gameProfileClass() {
        return firstOf(
                "com.mojang.authlib.GameProfile",
                "net.minecraft.util.com.mojang.authlib.GameProfile");
    }

    private static Class<?> propertyClass(Class<?> profileClass) {
        return firstOf(
                profileClass.getPackage().getName() + ".properties.Property",
                profileClass.getPackage().getName() + ".Property");
    }

    private static Class<?> firstOf(String first, String second) {
        try {
            Class<?> clazz = Class.forName(first);
            if (clazz != null) {
                return clazz;
            }
        } catch (ClassNotFoundException firstMissing) {
            // try the relocated pre-1.16.2 location
        }
        try {
            return Class.forName(second);
        } catch (ClassNotFoundException stillMissing) {
            throw new IllegalStateException("No authlib class on this server", stillMissing);
        }
    }

    /** setProfile lives on CraftMetaSkull's concrete class; find it by type. */
    private static Method findProfileMethod(Class<?> metaClass, Class<?> profileClass) {
        for (Method method : metaClass.getMethods()) {
            if (method.getName().equals("setProfile") && method.getParameterCount() == 1
                    && !Modifier.isStatic(method.getModifiers())) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 1 && types[0].isAssignableFrom(profileClass)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("No setProfile on " + metaClass.getName());
    }

    private static ItemStack head(String modern, String legacy, String legacyLegacy, short data) {
        Material material = safe(modern);
        if (material != null) {
            return new ItemStack(material, 1);
        }
        Material old = safe(legacy);
        if (old != null) {
            return new ItemStack(old, 1, data);
        }
        Material relit = safe(legacyLegacy);
        return relit == null ? null : new ItemStack(relit, 1, data);
    }

    private static Material safe(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            try {
                Material material = Material.getMaterial(name);
                return material != null ? material : Material.matchMaterial(name);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}