package dev.minted.bank;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks who has recently taken a hit so bank deposits can be refused while a
 * player is in combat. A fresh hit refreshes the window rather than stacking it:
 * the expiry is always reset to now + seconds. Only entity attacks count -
 * environmental damage (fall, lava, drowning) never fires
 * {@link EntityDamageByEntityEvent}, so it can never lock a player.
 *
 * <p>When disabled the whole feature is inert and every player reads as unlocked.
 */
public final class CombatLock implements Listener {

    private final boolean enabled;
    private final long durationMillis;
    private final boolean playersOnly;
    private final Map<UUID, Long> expiry = new HashMap<UUID, Long>();

    public CombatLock(boolean enabled, int seconds, boolean playersOnly) {
        this.enabled = enabled;
        this.durationMillis = seconds * 1000L;
        this.playersOnly = playersOnly;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (playersOnly && !(event.getDamager() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        expiry.put(victim.getUniqueId(), System.currentTimeMillis() + durationMillis);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        expiry.remove(event.getPlayer().getUniqueId());
    }

    /** Remaining lock in whole seconds (rounded up), or 0 when the player may deposit. */
    public int secondsLeft(Player player) {
        if (!enabled) {
            return 0;
        }
        Long until = expiry.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            expiry.remove(player.getUniqueId());
            return 0;
        }
        return (int) ((remaining + 999L) / 1000L);
    }
}
