package dev.minted.lang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Looks up player-facing strings by key.
 *
 * <p>Thin adapter over {@link LanguageManager}: a lookup for a player resolves
 * in that player's preferred language (set at runtime with
 * {@code /language set}), while a lookup with no player resolves in the live
 * server-wide language (config {@code lang.code}, changeable at runtime with
 * {@code /language global} or by editing the config and running
 * {@code /language reload}). English ships inside the jar and is the fallback
 * for every key, so a missing or partial translation never leaves a blank
 * line. Values may use {@code &} colour codes and {@code {placeholder}} tokens.
 */
public final class Messages {

    private final LanguageManager languages;

    private Messages(LanguageManager languages) {
        this.languages = languages;
    }

    public static Messages create(LanguageManager languages) {
        return new Messages(languages);
    }

    /** @param pairs alternating placeholder name and value, e.g. {@code "shop", "Spawn"} */
    public String get(Player player, String key, String... pairs) {
        return languages.get(player, key, pairs);
    }

    /** Global-language variant, used when no specific player is known. */
    public String get(String key, String... pairs) {
        return languages.getGlobal(key, pairs);
    }

    public void send(Player player, String key, String... pairs) {
        player.sendMessage(get(player, key, pairs));
    }

    public void send(CommandSender to, String key, String... pairs) {
        if (to instanceof Player) {
            send((Player) to, key, pairs);
        } else {
            to.sendMessage(get(key, pairs));
        }
    }
}