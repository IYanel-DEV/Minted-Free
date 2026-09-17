package dev.minted.compat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Version-safe tinted glass panes for menu borders and fillers.
 *
 * <p>Up to 1.12.2 a pane's colour is a data value on the single {@code
 * STAINED_GLASS_PANE} material; from 1.13 each colour is its own material
 * ({@code GRAY_STAINED_GLASS_PANE}, ...). We compile against 1.8, so every
 * material is reached by name at runtime. Different Paper/Spigot builds resolve
 * (or throw on) different spellings, so every lookup here is guarded and a pane
 * always comes back - a menu border must never be able to crash a menu.
 */
public final class Glass {

    /** The pane colours the theme uses; {@code data} is the 1.8 durability value. */
    public enum Tone {
        WHITE(0), LIGHT_GRAY(8), GRAY(7), BLACK(15),
        BLUE(11), LIGHT_BLUE(3), GREEN(13), LIME(5),
        RED(14), YELLOW(4), ORANGE(1);

        private final int data;

        Tone(int data) {
            this.data = data;
        }
    }

    private final boolean legacy;

    public Glass(ServerVersion version) {
        this.legacy = version.isBelow(1, 13);
    }

    public ItemStack pane(Tone tone) {
        // Pre-1.13 (and any build that reports an old version): one legacy
        // material plus a data value for the colour.
        if (legacy) {
            Material pane = safe("STAINED_GLASS_PANE");
            if (pane != null) {
                return new ItemStack(pane, 1, (short) tone.data);
            }
            // A build that misreports its version: fall through to the modern names.
        }
        // 1.13+: each colour is its own material. Modern-context servers resolve
        // the coloured names directly; legacy-context servers rewrite every lookup
        // to a LEGACY_* constant, whose only pane is LEGACY_STAINED_GLASS_PANE
        // (colour via the data value). Any attempt that throws just counts as null.
        Material modern = safe(tone.name() + "_STAINED_GLASS_PANE");
        if (modern == null) {
            modern = safe("GLASS_PANE");
        }
        if (modern == null) {
            modern = safe("LEGACY_STAINED_GLASS_PANE");
        }
        if (modern == null) {
            modern = safe("GLASS");
        }
        // Applying the 1.8 data value everywhere is harmless on modern materials
        // (they ignore durability) and is exactly what legacy materials need.
        return new ItemStack(modern == null ? Material.BARRIER : modern, 1, (short) tone.data);
    }

    /** valueOf first, then the lenient lookups - and never lets an exception out. */
    private static Material safe(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            // fall through to the lenient lookups
        }
        try {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
            return Material.matchMaterial(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}