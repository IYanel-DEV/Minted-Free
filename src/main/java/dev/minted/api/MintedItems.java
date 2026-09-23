package dev.minted.api;

import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The in-jar item registry API: answers "what items exist, and on which
 * Minecraft version" for every release from 1.8 up, without touching a server's
 * live {@link Material} enum where not needed.
 *
 * <p>Two groups of queries:
 * <ul>
 *   <li><b>Per-version knowledge</b> ({@link #isAvailable(String, int)},
 *       {@link #atVersion(String, int)}, {@link #itemsFor(int)}): pure registry
 *       data, deterministic for any 1.x minor - useful to answer "does 1.14
 *       have the crossbow?" while running on any server.</li>
 *   <li><b>Live server</b> ({@link #item(String)}, {@link #material(String)}):
 *       resolution against the running server's Material enum, including the
 *       durability a legacy-era variant needs.</li>
 * </ul>
 *
 * <p>Get an instance with {@link MintedAPI#items()}; it is also registered as
 * a Bukkit service ({@code MintedItems.class}) so other plugins can resolve it
 * through the services manager like {@link MintedAPI#economy()}.
 */
public final class MintedItems {

    private final MaterialLookup lookup;
    private final ServerVersion server;

    public MintedItems(MaterialLookup lookup, ServerVersion server) {
        this.lookup = lookup;
        this.server = server;
    }

    // --- live server -------------------------------------------------------

    /** The item as the running server can carry it, or null when unavailable. */
    public MintedItem item(String key) {
        MaterialLookup.Resolved resolved = lookup.item(key);
        return resolved == null ? null : new MintedItem(resolved);
    }

    /** The running server's material for {@code key}, or null when unavailable. */
    public Material material(String key) {
        return lookup.material(key);
    }

    /** Whether the running server can carry {@code key}. */
    public boolean isAvailable(String key) {
        return lookup.available(key);
    }

    // --- per-version knowledge ----------------------------------------------

    /** Whether {@code key} exists on Minecraft {@code 1.<minor>}. */
    public boolean isAvailable(String key, int minor) {
        return lookup.availableOn(key, minor);
    }

    /** The earliest 1.x minor that introduced {@code key} (1 when unknown). */
    public int since(String key) {
        return MaterialLookup.since(key);
    }

    /**
     * How {@code key} looks on Minecraft {@code 1.<minor>}, purely from the
     * registry: the canonical enum name and the durability for pre-1.13 eras.
     * Returns an unavailable item when the version predates the item, or null
     * when the key is unknown.
     */
    public MintedItem atVersion(String key, int minor) {
        MaterialLookup.Resolved resolved = lookup.known(key, minor);
        return resolved == null ? null : new MintedItem(resolved);
    }

    /** Every registry key Minecraft {@code 1.<minor>} can carry, in catalog order. */
    public List<String> itemsFor(int minor) {
        return Collections.unmodifiableList(new ArrayList<String>(lookup.keysFor(minor)));
    }

    /** Every registry key Minted knows about. */
    public List<String> allKeys() {
        return itemsFor(server.getMinor() + 100); // a version past everything
    }

    /** The running server's 1.x minor (e.g. 26 for 1.26). */
    public int serverMinor() {
        return server.getMinor();
    }
}