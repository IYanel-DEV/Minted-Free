package dev.minted.sound;

import org.bukkit.Sound;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a {@link Sound} by name across versions.
 *
 * <p>The {@code Sound} enum was renamed after 1.8, so a name that exists on the
 * compile target may be gone on the running server and vice versa. Callers give
 * a 1.8-style name; this tries it and its known modern spelling, returning the
 * first that resolves and {@code null} if neither does - a missing sound is a
 * silent no-op, never a crash.
 */
public final class Sounds {

    // 1.8 name -> its post-1.13 spelling. Only the handful the plugin plays.
    private static final Map<String, String> MODERN = new LinkedHashMap<String, String>();

    static {
        MODERN.put("NOTE_BASS", "BLOCK_NOTE_BLOCK_BASS");
        MODERN.put("NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING");
        MODERN.put("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
        MODERN.put("LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
        MODERN.put("ANVIL_LAND", "BLOCK_ANVIL_LAND");
        MODERN.put("CLICK", "UI_BUTTON_CLICK");
    }

    private Sounds() {
    }

    /** The sound for {@code name}, trying its modern spelling too, or null. */
    public static Sound resolve(String name) {
        Sound direct = tryValueOf(name);
        if (direct != null) {
            return direct;
        }
        String modern = MODERN.get(name);
        return modern == null ? null : tryValueOf(modern);
    }

    private static Sound tryValueOf(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
