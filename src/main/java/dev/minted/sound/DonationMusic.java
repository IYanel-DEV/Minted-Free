package dev.minted.sound;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * "Cool music": a short rising fanfare for the player a donation lands on. The
 * notes are scheduled a few ticks apart so they come across as a melody instead
 * of one burst. A per-receiver cooldown stops donation spam from replaying the
 * jingle repeatedly - when it is within {@code donate.music-cooldown-seconds}
 * (default 5s) of the last play for that player, this session is a no-op.
 */
public final class DonationMusic {

    /** Note, then the shift in pitch for the next notes, in ticks. */
    private static final String[] MELODY = {"NOTE_PLING", "NOTE_PLING", "NOTE_PLING", "ORB_PICKUP", "LEVEL_UP"};
    private static final float[] PITCHES = {1.0F, 1.2F, 1.4F, 1.0F, 1.0F};
    private static final long[] DELAYS = {0L, 4L, 8L, 12L, 16L};

    private final Plugin plugin;
    private final boolean enabled;
    private final float volume;
    private final long cooldownMillis;
    private final ConcurrentMap<Player, Long> lastPlayed = new ConcurrentHashMap<Player, Long>();

    public DonationMusic(Plugin plugin, ConfigurationSection soundConfig, long cooldownMillis) {
        this.plugin = plugin;
        this.enabled = soundConfig != null && soundConfig.getBoolean("enabled", true);
        this.volume = soundConfig != null ? (float) soundConfig.getDouble("volume", 1.0) : 1.0F;
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * Plays the fanfare unless this receiver heard it too recently (spam guard).
     */
    public void play(Player receiver) {
        if (!enabled || receiver == null || !receiver.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPlayed.get(receiver);
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastPlayed.put(receiver, now);
        for (int i = 0; i < MELODY.length; i++) {
            final Sound sound = Sounds.resolve(MELODY[i]);
            if (sound == null) {
                continue;
            }
            final float pitch = PITCHES[i];
            final Player target = receiver;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isOnline()) {
                        target.playSound(target.getLocation(), sound, volume, pitch);
                    }
                }
            }.runTaskLater(plugin, DELAYS[i]);
        }
    }
}