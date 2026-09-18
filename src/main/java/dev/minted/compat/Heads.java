package dev.minted.compat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Version-safe player heads for menus.
 *
 * <p>Until 1.13 a player head is the {@code SKULL_ITEM} material with a data
 * value of 3; from 1.13 it is its own {@code PLAYER_HEAD} material. The owner
 * is applied through {@link SkullMeta#setOwner}, which every supported release
 * understands, so a head always renders even when a server cannot resolve the
 * skin; every lookup is guarded so a head can never crash a menu.
 */
public final class Heads {

    public static final short PLAYER_DATA = 3;

    private Heads() {
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