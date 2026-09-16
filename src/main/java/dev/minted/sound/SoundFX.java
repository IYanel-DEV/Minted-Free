package dev.minted.sound;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Plays one distinct sound on each money moment, to the one player who should
 * hear it. Everything is inert when {@code sounds.enabled} is false or a name
 * cannot be resolved on this server, so a wrong config value never throws. All
 * three call sites are already on the main thread, as {@code playSound} needs.
 */
public final class SoundFX {

    private final boolean enabled;
    private final float volume;
    private final Sound paySent;
    private final Sound payReceived;
    private final Sound requestReceived;

    public SoundFX(ConfigurationSection config) {
        boolean on = config != null && config.getBoolean("enabled", true);
        this.enabled = on;
        this.volume = config != null ? (float) config.getDouble("volume", 1.0) : 1.0F;
        this.paySent = on ? Sounds.resolve(name(config, "pay-sent", "NOTE_BASS")) : null;
        this.payReceived = on ? Sounds.resolve(name(config, "pay-received", "ORB_PICKUP")) : null;
        this.requestReceived = on ? Sounds.resolve(name(config, "request-received", "NOTE_PLING")) : null;
    }

    public void paySent(Player sender) {
        play(sender, paySent, 0.8F);
    }

    public void payReceived(Player receiver) {
        play(receiver, payReceived, 1.4F);
    }

    public void requestReceived(Player receiver) {
        play(receiver, requestReceived, 1.2F);
    }

    private void play(Player player, Sound sound, float pitch) {
        if (!enabled || sound == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static String name(ConfigurationSection config, String key, String fallback) {
        return config == null ? fallback : config.getString(key, fallback);
    }
}
