package dev.minted.compat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Version-safe tinted glass panes for menu borders and fillers.
 *
 * <p>Up to 1.12.2 a pane's colour is a data value on the single {@code
 * STAINED_GLASS_PANE} material; from 1.13 each colour is its own material
 * ({@code GRAY_STAINED_GLASS_PANE}, ...). We compile against 1.8, so the modern
 * materials are reached by name and the legacy one by {@link Material#valueOf}
 * (so nothing links against a constant the running server lacks). A colour that
 * cannot be resolved falls back to a plain pane rather than throwing.
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
        if (legacy) {
            Material pane = Material.valueOf("STAINED_GLASS_PANE");
            return new ItemStack(pane, 1, (short) tone.data);
        }
        Material modern = Material.getMaterial(tone.name() + "_STAINED_GLASS_PANE");
        if (modern == null) {
            modern = Material.getMaterial("GLASS_PANE");
        }
        return new ItemStack(modern);
    }
}
