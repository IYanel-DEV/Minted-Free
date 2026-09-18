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
 * pointing at a {@code textures.minecraft.net} skin. There is no pure Bukkit
 * API for that, so the profile is built reflectively through the authlib
 * classes bundled with every server and applied through
 * {@code CraftMetaSkull#setProfile}. Every version and fork behaves slightly
 * differently, so the whole path is guarded: if any step fails Minted returns
 * the {@linkplain #icon(String, Material) plain-material fallback} and a menu
 * can never break because of a head. The texture values baked in here are
 * those of the public Minecraft-Heads library and are cheap for a client to
 * fetch from Mojang's servers.
 */
public final class Heads {

    public static final short PLAYER_DATA = 3;

    // Textured icons, keyed by the UI glyph they represent. Values are base64
    // profile texture payloads from the public Minecraft-Heads library.
    private static final String T_BACK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2RjOWU0ZGNmYTQyMjFhMWZhZGMxYjViMmIxMWQ4YmVlYjU3ODc5YWYxYzQyMzYyMTQyYmFlMWVkZDUifX19";
    private static final String T_NEXT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU2YTM2MTg0NTllNDNiMjg3YjIyYjdlMjM1ZWM2OTk1OTQ1NDZjNmZjZDZkYzg0YmZjYTRjZjMwYWI5MzExIn19fQ==";
    private static final String T_MONEY =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBhN2I5NGM0ZTU4MWI2OTkxNTlkNDg4NDZlYzA5MTM5MjUwNjIzN2M4OWE5N2M5MzI0OGEwZDhhYmM5MTZkNSJ9fX0=";
    private static final String T_BANK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTFlZGYxNmM0MWQxOTRjNzMxZTMzZmRkOWMyYjllNWVkZDQ1MGJjMzNjYTcwNDM2NTI4YTA1Mzg5ZDdmY2RhMiJ9fX0=";
    private static final String T_WALLET =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmU3ZTNlOGFiMDYwZTY0ZDAyNTZiMzY4OGU2MmQ0MzNlYWIzNDFhMTU3ZjJhNzMzZWQ0MzQ1MGZlZTRlNzI2NCJ9fX0=";
    private static final String T_POUCH =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM4MWM1MjlkNTJlMDNjZDc0YzNiZjM4YmI2YmEzZmRlMTMzN2FlOWJmNTAzMzJmYWE4ODllMGEyOGU4MDgxZiJ9fX0=";
    private static final String T_HOME =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2IwYTY4OWU1NTc0YWY4MDNlMDFlZmRhMDExMTRjOWRjZDM1N2U5YzQyNzg5NjViYjViNGRiYjVjMzM4NzM0NyJ9fX0=";
    private static final String T_CONFIRM =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0=";
    private static final String T_NONE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjNiMzI5YzEwNTI3N2JmNGE4NTYyMWUzZTVhNDk2ZTJhYTM3NjU4NTY2ZTA3ZGZhMWNkYmI3YmI2YzE5YTVhNiJ9fX0=";
    private static final String T_PAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjY4ZTNlM2YzOTE1NGE4OTc2Nzg2YjJlYjZmNzkxMDhiOTMzZmRiOWJlMDMzYmE2YWViY2FhYWZmZjE4ZTNlMyJ9fX0=";
    private static final String T_CROWN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTFiMGE1YmJjNjk3YzBjNDJhNmNmMWI5YzRjNDQzNWIwNzMyMmZjZTViYjI3ZDgyYjY5MzA4NDNlNWFiN2EwOSJ9fX0=";
    private static final String T_FLAME =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI1OGI4M2YwNzYxOGVhNzlhMWExMjAyYjVhNzdiMTRkZjFjOGUzNWI2ZTFkZWI4YTI2ZDg5NzZmODUzNjBjMyJ9fX0=";
    private static final String T_BOOKS =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWFmNjQ1ZjQ4MjRlNTRkMjY5NzJkMGJmNzIxMWI2ODE1MzgyMWRjM2EwZGY5OTJlNzY2NWNiMjFjNjk2OTBkYSJ9fX0=";
    private static final String T_SILVER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM0YjI3YmZjYzhmOWI5NjQ1OTRiNjE4YjExNDZhZjY5ZGUyNzhjZTVlMmUzMDEyY2I0NzFhOWEzY2YzODcxIn19fQ==";

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