package dev.minted.api;

import dev.minted.compat.MaterialLookup;
import org.bukkit.Material;

/**
 * The immutable result of an item query through {@link MintedItems}: which
 * Minecraft version introduced the item, what enum name that version uses, and
 * - for the running server - the actual {@link Material} to use.
 *
 * <p>Every key is a Minted registry key (e.g. {@code red_wool},
 * {@code netherite_sword}, {@code cherry_trapdoor}). The same key resolves on
 * 1.8 as a legacy constant plus a durability value and on 1.13+ as its own
 * enum constant; {@link #data()} is the durability to attach on the old eras
 * so red wool is red and a creeper head is a creeper.
 */
public final class MintedItem {

    private final String key;
    private final int since;
    private final String name;
    private final Material material;
    private final short data;
    private final boolean available;

    MintedItem(MaterialLookup.Resolved resolved) {
        this.key = resolved.key();
        this.since = resolved.since();
        this.name = resolved.name();
        this.material = resolved.material();
        this.data = resolved.data();
        this.available = resolved.isAvailable();
    }

    /** The registry key, e.g. {@code red_wool}. */
    public String key() {
        return key;
    }

    /** The earliest 1.x minor (e.g. 13, 16, 21) that introduced this item. */
    public int since() {
        return since;
    }

    /**
     * The enum constant this item wears: the running server's material name for
     * {@link MintedItems#item}, or the canonical name for the queried 1.x minor
     * for {@link MintedItems#atVersion}. Null when unavailable on that version.
     */
    public String name() {
        return name;
    }

    /**
     * The actual {@link Material} on this server, or null for version-knowledge
     * queries ({@link MintedItems#atVersion}) or unavavailable items.
     */
    public Material material() {
        return material;
    }

    /**
     * The durability/variant data a pre-1.13 item needs to stay distinct (dye
     * colour, wood type, skull type). Modern items always carry 0.
     */
    public short data() {
        return data;
    }

    /** False when the queried version predates the item or it is unknown. */
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String toString() {
        return "MintedItem[" + key + (available ? "" : " (unavailable)") + " since=1." + since
                + " name=" + name + " material=" + material + " data=" + data + "]";
    }
}